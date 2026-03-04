# Socket Chat App — Complete Project Documentation

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Project Structure](#2-project-structure)
3. [How to Build and Run](#3-how-to-build-and-run)
4. [Architecture Overview](#4-architecture-overview)
5. [Core Concepts](#5-core-concepts)
   - [What is a Socket?](#51-what-is-a-socket)
   - [TCP Protocol](#52-tcp-protocol)
   - [ServerSocket vs Socket](#53-serversocket-vs-socket)
   - [DataOutputStream and DataInputStream](#54-dataoutputstream-and-datainputstream)
   - [Threads](#55-threads)
   - [CopyOnWriteArrayList](#56-copyonwritearraylist)
6. [File-by-File Line-by-Line Explanation](#6-file-by-file-line-by-line-explanation)
   - [ServerLogger.java](#61-serverloggerjava)
   - [Server.java](#62-serverjava)
   - [ClientHandler.java](#63-clienthandlerjava)
   - [Client.java](#64-clientjava)
7. [Full Project Flow](#7-full-project-flow)
   - [Phase 1: Server Starts](#phase-1-server-starts)
   - [Phase 2: Client Connects](#phase-2-client-connects)
   - [Phase 3: Username Handshake](#phase-3-username-handshake)
   - [Phase 4: Client Sends a Message](#phase-4-client-sends-a-message)
   - [Phase 5: Second Client Joins](#phase-5-second-client-joins)
   - [Phase 6: Clean Quit](#phase-6-clean-quit)
   - [Phase 7: Unexpected Disconnect](#phase-7-unexpected-disconnect)
8. [Execution Order Table](#8-execution-order-table)
9. [Thread Map](#9-thread-map)
10. [Logging Reference](#10-logging-reference)
11. [Glossary](#11-glossary)

---

## 1. Project Overview

A **TCP socket-based multi-client chat application** written in Java 21 using only the Java
standard library. No external frameworks or dependencies.

Multiple clients connect to a single server. Every message one client sends is
**broadcast** to all connected clients by the server. Clients disconnect by typing `/quit`.

| Property | Value |
|---|---|
| Language | Java 21 |
| Build tool | Apache Maven |
| Transport | TCP (raw sockets) |
| Wire format | `DataOutputStream` / `DataInputStream` (UTF-8 strings) |
| Server model | Thread-per-client |
| External libraries | None |

---

## 2. Project Structure

```
Socket Chat App/
├── pom.xml
└── src/main/java/org/example/
    ├── Server.java          entry point — opens port, accepts connections, broadcasts
    ├── ClientHandler.java   per-client thread — reads messages, routes them
    ├── Client.java          chat client — connects, sends, receives
    └── ServerLogger.java    structured logging utility
```

---

## 3. How to Build and Run

```bash
# Compile
mvn compile

# Package
mvn package -DskipTests

# Clean and recompile
mvn clean compile
```

```bash
# Terminal 1 — start server FIRST
mvn exec:java -Dexec.mainClass="org.example.Server"

# Terminal 2+ — start one or more clients AFTER server is up
mvn exec:java -Dexec.mainClass="org.example.Client"
```

- Server listens on **port 5000**.
- Client connects to **localhost:5000**.
- Type `/quit` to disconnect.

---

## 4. Architecture Overview

```
                         SERVER PROCESS
┌────────────────────────────────────────────────────────────┐
│                                                            │
│  ServerSocket  ──accept()──► spawns ClientHandler thread  │
│  (port 5000)                 for every new connection      │
│                                                            │
│  clients list (CopyOnWriteArrayList)                       │
│  ┌──────────────────┐  ┌──────────────────┐               │
│  │ ClientHandler    │  │ ClientHandler    │               │
│  │ Thread: alice    │  │ Thread: bob      │               │
│  │ DataInputStream  │  │ DataInputStream  │               │
│  │ DataOutputStream │  │ DataOutputStream │               │
│  └────────┬─────────┘  └────────┬─────────┘               │
│           │  broadcast()        │                          │
│           └────────────►◄───────┘                          │
└────────────────────────────────────────────────────────────┘
             │ TCP                │ TCP
    ┌────────┴──────┐    ┌────────┴──────┐
    │  Client alice │    │  Client bob   │
    │  main thread  │    │  main thread  │  ← keyboard → send
    │  recv thread  │    │  recv thread  │  ← network  → print
    └───────────────┘    └───────────────┘
```

---

## 5. Core Concepts

### 5.1 What is a Socket?

A **socket** is a software endpoint for a two-way network communication channel.
Think of it like a telephone call — one side dials (client), the other answers (server),
and both sides can talk and listen at the same time until one hangs up.

```
Client Socket                    Server Socket
    │                                │
    │──── TCP connection ────────────│
    │                                │
    │── write bytes ────────────────►│
    │◄─────────────────── write bytes│
    │                                │
    │── close() ────────────────────►│ close()
```

In Java, sockets live in the `java.net` package:
- `Socket` — one connected endpoint (client side, or per-client server side).
- `ServerSocket` — listens for incoming connections (server side only).

### 5.2 TCP Protocol

This project uses **TCP (Transmission Control Protocol)**:

| Feature | TCP | UDP |
|---|---|---|
| Connection | Requires handshake before data | No setup needed |
| Delivery | Guaranteed, in order, no duplicates | No guarantee |
| Use case | Chat, HTTP, file transfer | Video stream, games |

TCP ensures every message alice sends arrives at bob in the correct order. This is
essential for a chat app.

**TCP Three-Way Handshake** (happens when `new Socket(host, port)` is called):

```
Client                     Server
  │                           │
  │──── SYN ─────────────────►│   "I want to connect"
  │◄─── SYN-ACK ──────────────│   "OK, I acknowledge"
  │──── ACK ─────────────────►│   "Connection established"
  │                           │
  │  <<< data flows both ways >>>
```

### 5.3 ServerSocket vs Socket

| Class | Purpose | Who creates it |
|---|---|---|
| `ServerSocket` | Binds a port, listens for clients, never sends/receives data | Server only |
| `Socket` | The actual connection — has input and output streams for data | Client creates one; server gets one per `accept()` call |

```java
// Server side
ServerSocket serverSocket = new ServerSocket(5000);  // open port
Socket socket = serverSocket.accept();               // wait for client, get connection

// Client side
Socket socket = new Socket("localhost", 5000);       // connect to server
```

### 5.4 DataOutputStream and DataInputStream

In this project, messages travel as **UTF-8 encoded strings** using Java's
`DataOutputStream` and `DataInputStream`.

```java
// Sending a string
DataOutputStream out = new DataOutputStream(socket.getOutputStream());
out.writeUTF("hello");    // writes: 2-byte length prefix + UTF-8 bytes
out.flush();              // pushes buffered bytes into the TCP stream

// Receiving a string
DataInputStream in = new DataInputStream(socket.getInputStream());
String text = in.readUTF();   // reads 2-byte length, then reads that many bytes
```

**`writeUTF` wire format:**

```
┌──────────────────┬───────────────────────────────┐
│  2 bytes         │  N bytes                      │
│  length of string│  UTF-8 encoded string content │
└──────────────────┴───────────────────────────────┘
```

The length prefix lets `readUTF` know exactly how many bytes to read for one string.
This is called a **length-prefixed protocol** — no delimiter characters needed.

**`flush()` is required** because `DataOutputStream` may buffer bytes internally.
Without `flush()`, the bytes might sit in the buffer and never be sent. After `writeUTF`,
always call `out.flush()` to push the data into the TCP socket immediately.

**Comparison to the previous approach (`ObjectOutputStream`):**

| | `DataOutputStream` / `DataInputStream` | `ObjectOutputStream` / `ObjectInputStream` |
|---|---|---|
| Sends | Raw strings (`writeUTF`) | Serialized Java objects (`writeObject`) |
| Protocol | Simple 2-byte length prefix | Java serialization protocol (heavier) |
| Speed | Faster, smaller overhead | More overhead |
| Flexibility | Strings only (by default) | Any `Serializable` object |

### 5.5 Threads

A **thread** is the smallest unit of execution. This app uses three kinds:

| Thread | Created by | Purpose |
|---|---|---|
| `main` (Server) | JVM | Runs `Server.main()`, blocks on `accept()` |
| `ClientHandler-<name>` | Server for each client | Reads messages from one client, calls `broadcast()` |
| `main` (Client) | JVM | Reads keyboard input, sends messages |
| `ReceiveThread-<name>` | Client | Reads messages from server, prints them |

**Why threads?**

Both reading from a socket and reading keyboard input are **blocking** operations —
they pause execution until data arrives. You cannot do both on the same thread without
one permanently blocking the other. Threads let both happen simultaneously.

```
Client main thread:      blocks on scanner.nextLine()  ← keyboard
Client receive thread:   blocks on in.readUTF()         ← network
```

**Two ways threads are created in this project:**

```java
// Way 1: extend Thread (ClientHandler)
public class ClientHandler extends Thread {
    @Override
    public void run() { /* runs on new thread */ }
}
ClientHandler h = new ClientHandler(socket);
h.start();   // spawns new OS thread, calls run()

// Way 2: lambda passed to new Thread (Client receive thread)
Thread t = new Thread(() -> {
    // runs on new thread
}, "ReceiveThread-alice");
t.setDaemon(true);
t.start();
```

**Daemon threads:**

```java
receiveThread.setDaemon(true);
```

A daemon thread does not prevent the JVM from exiting. When the client's `main` thread
finishes (after `/quit`), the JVM exits immediately, taking the daemon receive thread with
it — even if `in.readUTF()` is still blocked.

### 5.6 CopyOnWriteArrayList

```java
public static final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
```

Multiple `ClientHandler` threads all call `Server.broadcast()` (which iterates the list)
and `Server.removeClient()` (which removes from the list) at the same time.

A plain `ArrayList` is not thread-safe — concurrent modification causes
`ConcurrentModificationException` or silent data corruption.

`CopyOnWriteArrayList` solves this:
- **Write** (`add`, `remove`): copies the entire internal array, modifies the copy,
  atomically swaps the reference.
- **Read / iterate**: uses a snapshot of the array taken at the start — safe even if
  another thread removes an element mid-iteration.

```
Before remove(bob):
  internal array ──► [ alice | bob | carol ]

remove(bob):
  1. copy  ──► [ alice | ___ | carol ]
  2. shift ──► [ alice | carol ]
  3. swap  ──► internal array now points to new array
               old array is garbage collected
```

---

## 6. File-by-File Line-by-Line Explanation

---

### 6.1 `ServerLogger.java`

Explained first because every other file calls it.

```java
package org.example;
```
Declares the package. All four files share `org.example`, so they can reference each other
by simple class name — no `import` statements between them.

```java
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
```
- `IOException` — thrown by `FileHandler` constructor if the log file cannot be created.
- `LocalDateTime` — Java 8+ date/time. Used to timestamp each log line.
- `FileHandler` — a JUL handler that writes log records to a file on disk.
- `Formatter` — abstract base class; subclassed anonymously to control the line format.
- `LogRecord` — carries the level, message, and metadata for one log entry.
- `Logger` — Java's built-in logging framework (`java.util.logging`, shortened to JUL).

```java
public class ServerLogger {
```
Plain `public class`. No `extends`, no `implements`. Not a thread, not serializable.
Just a container for static methods and the shared logger.

```java
    private static final Logger logger = Logger.getLogger("ChatServer");
```
- `private` — the raw `Logger` is hidden. Callers use the named methods below.
- `static` — one shared logger for the whole server process.
- `final` — the reference never changes.
- `Logger.getLogger("ChatServer")` — fetches (or creates) the logger named `"ChatServer"`
  from the JVM-wide logger registry.

```java
    static {
        try {
            FileHandler fileHandler = new FileHandler("server_logs.txt", true);
            fileHandler.setFormatter(new Formatter() {
                @Override
                public String format(LogRecord record) {
                    return record.getLevel() + " | " + record.getMessage() + System.lineSeparator();
                }
            });
            logger.addHandler(fileHandler);
        } catch (IOException e) {
            logger.severe("Failed to initialize log file: " + e.getMessage());
        }
    }
```
- `static { ... }` — **static initializer block**. Runs exactly once, when the JVM first
  loads the `ServerLogger` class (which happens on the first call to any `ServerLogger`
  method, i.e., `ServerLogger.serverStarted(PORT)` in `Server.main`).
- `new FileHandler("server_logs.txt", true)`:
  - First argument — the file path. Relative paths resolve against the directory where the
    JVM is launched (the project root when using `mvn exec:java`). The file is created if
    it does not exist.
  - Second argument `true` — **append mode**. Existing log lines are preserved across
    server restarts. Without `true`, the file is truncated to zero bytes on every start.
- `fileHandler.setFormatter(new Formatter() { ... })` — replaces JUL's default XML
  formatter with a plain-text one-line-per-record format:
  ```
  INFO | SERVER STARTED | Port: 5000 | Time: 2026-03-04T14:00:00
  WARNING | USER DISCONNECTED UNEXPECTEDLY | Username: bob | Time: ...
  ```
- `record.getLevel()` — the severity string: `INFO`, `WARNING`, or `SEVERE`.
- `record.getMessage()` — the log message string passed by the calling method.
- `System.lineSeparator()` — platform-correct line ending (`\r\n` on Windows, `\n` on Unix).
- `logger.addHandler(fileHandler)` — attaches the file handler to the logger. The logger
  now has two destinations: the default console handler and the new file handler. Every
  log call goes to both simultaneously.
- `catch (IOException e)` — if the file cannot be opened (permission denied, invalid path),
  logs the failure to the console via `logger.severe`. The server continues running; it
  just will not write to the file.

```java
    private ServerLogger() {
    }
```
`private` constructor — utility class, never instantiated.

```java
    public static void serverStarted(int port) { ... }
    public static void serverStopped() { ... }
    public static void newClientConnected() { ... }
    public static void userConnected(String username, String ip) { ... }
    public static void userDisconnected(String username) { ... }
    public static void userDisconnectedUnexpectedly(String username) { ... }
    public static void messageBroadcast(String message) { ... }
    public static void privateMessage(String from, String to, String text) { ... }
    public static void error(String message) { ... }
```
Each method calls `logger.info(...)`, `logger.warning(...)`, or `logger.severe(...)`.
Because the `FileHandler` is attached to `logger`, every call writes to both the console
and `server_logs.txt` automatically — no per-method file logic needed.

---

### 6.2 `Server.java`

```java
package org.example;
```
Same package — can use `ClientHandler`, `ServerLogger` without imports.

```java
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
```
- `ServerSocket` — binds a port and listens for TCP connections.
- `Socket` — represents one active TCP connection returned by `accept()`.
- `List` — the generic list interface. Declared type for `clients`.
- `Map` — the generic map interface. Declared type for `clientMap`.
- `ConcurrentHashMap` — thread-safe hash map. Used for the username → handler lookup.
- `CopyOnWriteArrayList` — the thread-safe list implementation (see section 5.6).

```java
public class Server {
```
Not a thread. Not instantiated. A static utility class — a namespace for the server's
global state and shared methods.

```java
    public static final int PORT = 5000;
```
- `public` — `Client.java` could reference it, though it uses its own constant.
- `static final` — compile-time constant. `SCREAMING_SNAKE_CASE` by Java convention.
- `5000` — TCP port number. The OS binds this port when `new ServerSocket(PORT)` is called.

```java
    public static final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    public static final Map<String, ClientHandler> clientMap = new ConcurrentHashMap<>();
```
- `clients` — the global list of every currently connected `ClientHandler`. Used by
  `broadcast()` to iterate all clients.
- `clientMap` — maps each username string to its `ClientHandler`. Used by `sendPrivate()`
  for O(1) lookup by name. `ConcurrentHashMap` is thread-safe for concurrent `put`, `get`,
  and `remove` from multiple `ClientHandler` threads without extra synchronization.
- Both are `public static final`: the reference never changes; the contents do.

```java
    private Server() {
        // utility class — no instances
    }
```
Private constructor — enforces the utility class pattern.

```java
    public static void main(String[] args) {
        ServerLogger.serverStarted(PORT);
```
- JVM entry point.
- Logs server startup immediately. If `new ServerSocket(PORT)` fails below, the log still
  shows the server attempted to start.

```java
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
```
- `try (...)` — **try-with-resources**. `ServerSocket` implements `AutoCloseable`.
  When the `try` block exits (exception or normal), `serverSocket.close()` is called
  automatically, releasing port 5000.
- `new ServerSocket(PORT)` — this single line does three things in the OS:
  1. Creates a TCP socket.
  2. **Binds** it to port 5000 (reserves the port for this process).
  3. Puts it in the **listening** state (the OS starts queuing incoming connections).
  - Throws `BindException` if port 5000 is already in use.
  - Throws `IOException` for any other OS-level failure.

```java
            while (true) {
```
Infinite loop — the server runs forever until killed or an exception escapes the loop.

```java
                Socket socket = serverSocket.accept();
```
- `accept()` — **blocking call**. The current thread (main thread) is suspended here.
  The OS puts it to sleep and wakes it only when a client completes the TCP three-way
  handshake. Returns a new `Socket` representing the server's end of that connection.
- The `ServerSocket` is not consumed — it keeps listening. Each `accept()` call handles
  exactly one new client.

```java
                ServerLogger.newClientConnected();
```
Logs immediately after `accept()` returns. At this point the TCP connection is open but
the username is not yet known (that happens inside `ClientHandler`).

```java
                ClientHandler handler = new ClientHandler(socket);
```
Creates a new `ClientHandler` object. The thread has NOT started yet. Just an object
in memory holding a reference to the socket.

```java
                clients.add(handler);
```
Adds to the global list **before** `start()`. This ensures that if the handler's first
broadcast (join message) fires, the new client is already in the list and can receive it.

```java
                handler.start();
```
- `start()` is inherited from `Thread` (because `ClientHandler extends Thread`).
- Spawns a brand new OS thread. The new thread calls `handler.run()`.
- `start()` returns immediately on the main thread. Both threads now run concurrently.
- **Never call `handler.run()` directly** — that would block the main thread.

```java
        } catch (Exception e) {
            ServerLogger.error(e.getMessage());
        } finally {
            ServerLogger.serverStopped();
        }
```
- `catch (Exception e)` — catches anything that escapes the `while(true)` loop. In normal
  operation this never fires. Could fire if `accept()` fails due to an OS error.
- `e.getMessage()` — the exception's plain-English description.
- `finally` — always runs, logging that the server stopped. The `try-with-resources` also
  closes `serverSocket` here, releasing port 5000.

```java
    public static void broadcast(String message) {
        ServerLogger.messageBroadcast(message);
        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }
    }
```
- `public static` — called from `ClientHandler` threads as `Server.broadcast(...)`.
- `ServerLogger.messageBroadcast(message)` — logs every outgoing broadcast once.
- `for (ClientHandler client : clients)` — enhanced for-each loop. With
  `CopyOnWriteArrayList`, this iterates a **stable snapshot** of the list taken at loop
  start. If a client disconnects mid-loop, the iteration continues safely on the snapshot.
- `client.sendMessage(message)` — calls `sendMessage` on each handler, which writes the
  string to that client's socket via `DataOutputStream.writeUTF`.
- This method is called from multiple `ClientHandler` threads simultaneously (each one
  calls it independently when it receives a message). Thread safety is guaranteed by
  `CopyOnWriteArrayList` and by the fact that each `ClientHandler` writes to its own
  separate `DataOutputStream`.

```java
    public static void removeClient(ClientHandler client) {
        clients.remove(client);
        clientMap.values().remove(client);
    }
```
- Called from `ClientHandler.run()`'s `finally` block: `Server.removeClient(this)`.
- `clients.remove(client)` — removes from the broadcast list.
- `clientMap.values().remove(client)` — removes from the username lookup map. Uses
  value-based removal so the username key does not need to be known at call time.

```java
    public static void registerClient(String username, ClientHandler handler) {
        clientMap.put(username, handler);
    }
```
- Called by `ClientHandler` immediately after the username handshake completes.
- `clientMap.put(username, handler)` — inserts the `username → handler` pair so
  `sendPrivate()` can find this client by name in O(1).

```java
    public static void sendPrivate(String fromUsername, String toUsername, String text) {
        ClientHandler target = clientMap.get(toUsername);
        if (target == null) {
            ClientHandler sender = clientMap.get(fromUsername);
            if (sender != null) {
                sender.sendMessage("[Server]: User '" + toUsername + "' not found.");
            }
            return;
        }
        target.sendMessage("[PM from " + fromUsername + "]: " + text);
        ServerLogger.privateMessage(fromUsername, toUsername, text);
    }
```
- `clientMap.get(toUsername)` — O(1) lookup. Returns `null` if the user does not exist or
  has already disconnected.
- If `target == null` — looks up the sender's handler and delivers an error notice back to
  them only. No other client sees the message.
- `target.sendMessage(...)` — delivers the private message to the recipient only.
- `ServerLogger.privateMessage(...)` — logs the PM at `INFO` level on the server.
- The sender receives **no echo** here — `Client.java` already shows them what they typed.

---

### 6.3 `ClientHandler.java`

```java
package org.example;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
```
- `DataInputStream` — reads primitive types and UTF-8 strings from a byte stream.
  `readUTF()` reads a 2-byte length prefix then the string bytes.
- `DataOutputStream` — writes primitive types and UTF-8 strings to a byte stream.
  `writeUTF()` writes a 2-byte length prefix then the string bytes.
- `Socket` — the TCP connection to one specific client.

```java
public class ClientHandler extends Thread {
```
- `extends Thread` — `ClientHandler` IS a thread. It inherits `start()`, `run()`,
  `setName()`, and all other thread behavior.
- One `ClientHandler` instance is created per connected client. Each runs independently.

```java
    private final Socket socket;
```
- `private` — no other class accesses this directly.
- `final` — set in the constructor, never changes. This handler is permanently bound to
  this one client's socket.

```java
    private DataInputStream in;
    private DataOutputStream out;
```
- **Not `final`** — they cannot be assigned in the constructor because the socket must be
  open first, which only happens when the thread starts executing `run()`. Java's `final`
  field rule requires constructor assignment, so `final` cannot be used here.
- `DataInputStream in` — reads UTF strings sent by the client over the TCP connection.
- `DataOutputStream out` — writes UTF strings back to the client over the TCP connection.

```java
    private String username;
```
- Not `final` — assigned mid-execution after the username is read from the stream.
- `null` until the handshake completes. The `catch` block checks `username != null` to
  distinguish a pre-handshake failure from a post-handshake disconnect.

```java
    public ClientHandler(Socket socket) {
        this.socket = socket;
    }
```
- Constructor called by `Server.java`: `new ClientHandler(socket)`.
- `this.socket = socket` — `this.socket` is the field; `socket` is the parameter.
  The `this.` prefix disambiguates them when names collide.
- Thread NOT started yet. `Server` calls `handler.start()` on the next line.

```java
    @Override
    public void run() {
```
- `@Override` — tells the compiler this overrides `Thread.run()`. Compile error if it does not.
- `run()` is the method the new OS thread executes. Everything inside here runs on the
  `ClientHandler` thread, not the server's main thread.

```java
        try {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
```
- `socket.getInputStream()` — the raw byte stream coming from the client over TCP.
- `socket.getOutputStream()` — the raw byte stream going to the client over TCP.
- `new DataInputStream(...)` — wraps the raw stream, adding `readUTF()` and other
  typed-read methods.
- `new DataOutputStream(...)` — wraps the raw stream, adding `writeUTF()` and `flush()`.
- No stream header exchange happens here (unlike `ObjectOutputStream`). `DataInputStream`
  and `DataOutputStream` are lightweight wrappers — they start reading/writing immediately.

```java
            username = in.readUTF();
            setName("ClientHandler-" + username);
            Server.registerClient(username, this);
```
- `readUTF()` — **blocking call**. Returns the first string the client sends (the username).
- `setName(...)` — renames this thread to `"ClientHandler-alice"` for debugging.
- `Server.registerClient(username, this)` — inserts `"alice" → this handler` into
  `Server.clientMap` so private messages can reach this client by name.

```java
            ServerLogger.userConnected(username, socket.getInetAddress().getHostAddress());
```
- `socket.getInetAddress()` — returns the remote client's `InetAddress` object.
- `.getHostAddress()` — converts it to a dotted-decimal string: `"127.0.0.1"`.
- Logs the full connection event now that the username is known.

```java
            Server.broadcast(username + " joined the chat");
```
- Sends a join announcement to all connected clients (including this new one, since
  `clients.add(handler)` was called before `handler.start()`).
- `username + " joined the chat"` — plain string concatenation.

```java
            while (true) {
                String message = in.readUTF();
```
- Main receive loop. Runs forever until `break` or exception.
- `in.readUTF()` — **blocking call**. Thread sleeps here waiting for the next message
  from this client. Wakes when the client sends something.

```java
                if (message.equalsIgnoreCase("/quit")) {
                    Server.broadcast(username + " left the chat");
                    ServerLogger.userDisconnected(username);
                    break;
                }

                if (message.startsWith("@")) {
                    int spaceIndex = message.indexOf(' ');
                    if (spaceIndex > 1) {
                        String targetUsername = message.substring(1, spaceIndex);
                        String privateText = message.substring(spaceIndex + 1);
                        Server.sendPrivate(username, targetUsername, privateText);
                    } else {
                        sendMessage("[Server]: Usage: @username message");
                    }
                    continue;
                }

                Server.broadcast(username + ": " + message);
```
- **`/quit` branch** — broadcasts the leave notice, logs the disconnect, breaks the loop.
- **`@` branch** — private message parser:
  - `message.startsWith("@")` — detects a PM. Example input: `"@bob hey"`.
  - `message.indexOf(' ')` — finds the space between the username and the message text.
    Returns `-1` if no space exists.
  - `spaceIndex > 1` — requires at least one character after `@` before the space
    (i.e., `@` alone or `@ ` fails validation).
  - `message.substring(1, spaceIndex)` — extracts the target username. For `"@bob hey"`:
    `substring(1, 4)` → `"bob"`.
  - `message.substring(spaceIndex + 1)` — extracts the message text. For `"@bob hey"`:
    `substring(5)` → `"hey"`.
  - `Server.sendPrivate(username, targetUsername, privateText)` — routes the PM; only the
    recipient sees it.
  - `else` branch — malformed input (e.g., `"@"` or `"@bob"` with no text). Sends a usage
    hint back to the sender only.
  - `continue` — skips `broadcast()`. The message is never sent to all clients.
- **broadcast branch** — normal message; sent to all connected clients.

```java
        } catch (Exception e) {
            if (username != null) {
                ServerLogger.userDisconnectedUnexpectedly(username);
                Server.broadcast(username + " lost connection");
            } else {
                ServerLogger.error("Unnamed client disconnected: " + e.getMessage());
            }
```
- `catch (Exception e)` — catches `EOFException` (client closed stream), `SocketException`
  (network dropped), or any other I/O failure.
- `username != null` — checks whether the handshake completed. If the client disconnected
  before sending a username, we only log an error. If the username is known, we broadcast
  a "lost connection" notice.
- This block runs only on **unexpected** disconnects. Clean `/quit` hits `break` and
  bypasses this catch entirely.

```java
        } finally {
            Server.removeClient(this);
            close();
        }
```
- `finally` — always executes, regardless of how the `try` or `catch` exits.
- `Server.removeClient(this)` — `this` is the current `ClientHandler`. Removes it from
  the global list so future `broadcast()` calls skip this client.
- `close()` — calls the private cleanup method below.

```java
    public void sendMessage(String message) {
        try {
            out.writeUTF(message);
            out.flush();
        } catch (Exception e) {
            ServerLogger.error("Failed to send message to " + username + ": " + e.getMessage());
        }
    }
```
- `public` — called by `Server.broadcast()` and `Server.sendPrivate()`.
- `out.writeUTF(message)` — **serializes the string to bytes and writes them into the
  socket's output stream**, which sends them over TCP to the client. Writes: 2-byte length
  + UTF-8 bytes.
- `out.flush()` — pushes any internally buffered bytes into the TCP socket immediately.
  Without `flush()`, the data may sit in the buffer and never arrive.
- The `catch` logs and swallows the exception. This is intentional — if one client's
  socket is broken, `broadcast()` must continue to the other clients. The broken socket
  will be detected independently on the next `in.readUTF()` call in `run()`.

```java
    public String getUsername() {
        return username;
    }
```
- `public` — exposes the username field to external callers without making the field
  itself public. Currently unused in production paths (the map lookup in `sendPrivate`
  uses the string key directly), but useful for diagnostics and future features.

```java
    private void close() {
        try {
            socket.close();
        } catch (Exception e) {
            ServerLogger.error("Failed to close socket for " + username + ": " + e.getMessage());
        }
    }
```
- `socket.close()` — closes the TCP connection:
  1. Sends a TCP FIN packet to the client.
  2. Releases OS socket resources.
  3. Causes any blocked `readUTF()` on the client side to throw `IOException`.
- Closing the `socket` also closes the associated `in` and `out` streams.
- Wrapped in `try-catch` because `close()` can throw (e.g., already closed). We do not
  want a secondary exception thrown from inside `finally`.

---

### 6.4 `Client.java`

```java
package org.example;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.Scanner;
```
- `DataInputStream` / `DataOutputStream` — same as in `ClientHandler`, for reading and
  writing UTF strings over the socket.
- `Socket` — the TCP connection to the server.
- `Scanner` — wraps `System.in` (keyboard) to read lines of text.

```java
public class Client {
```
Not a thread class. Manages threads internally but does not extend `Thread`.

```java
    public static void main(String[] args) {

        try (
            Socket socket = new Socket("localhost", 5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            Scanner scanner = new Scanner(System.in)
        ) {
```
- `try (...)` — **try-with-resources** with four resources. All implement `AutoCloseable`.
  When the block exits (normally or via exception), they are closed in **reverse order**:
  `Scanner` → `DataInputStream` → `DataOutputStream` → `Socket`.
- `new Socket("localhost", 5000)` — **initiates the TCP connection**:
  1. Resolves `"localhost"` to `127.0.0.1`.
  2. Chooses a random local port (e.g., 54321).
  3. Performs the TCP three-way handshake with the server.
  4. Returns a connected `Socket`, or throws `ConnectException` ("Connection refused")
     if the server is not listening on port 5000.
- `new DataInputStream(socket.getInputStream())` — wraps the socket's raw input stream.
  No blocking occurs here; no stream header is exchanged (unlike `ObjectInputStream`).
- `new DataOutputStream(socket.getOutputStream())` — wraps the socket's raw output stream.
- `new Scanner(System.in)` — wraps the keyboard. `System.in` is the standard input stream.

```java
            System.out.print("Enter your username: ");
            String username = scanner.nextLine();
            out.writeUTF(username);
```
- `System.out.print(...)` — no newline, cursor stays on the same line.
- `scanner.nextLine()` — **blocks** until the user presses Enter. Returns the typed line.
- `out.writeUTF(username)` — sends the username string to the server. This triggers the
  server side's `in.readUTF()` in `ClientHandler.run()` to unblock and return the username.

```java
            // Thread to receive messages
            new Thread(() -> {
                try {
                    while (true) {
                        String message = in.readUTF();

                        // Skip messages that are our own broadcast echo
                        if (message.startsWith(username + ": ")) {
                            continue;
                        }

                        System.out.println(message);
                    }
                } catch (Exception e) {
                    System.out.println("Disconnected from server");
                }
            }).start();
```
- `new Thread(() -> { ... })` — creates and immediately starts an anonymous thread from
  a lambda. The lambda implements `Runnable.run()`.
- **Why a separate thread?** The main thread must block on `scanner.nextLine()` (keyboard).
  If we also called `in.readUTF()` on the main thread, one would permanently block the
  other. Two blocking operations require two threads.
- `in.readUTF()` — **blocks** waiting for the next message from the server.
- **Echo filter:** `message.startsWith(username + ": ")` — when the server broadcasts
  alice's own message back as `"alice: hello"`, this check matches and `continue` skips
  it. Alice already saw what she typed; printing the echo would be redundant. Messages
  from all other users, join/leave notices, and server messages do not start with
  `alice: ` so they are always printed.
- `catch (Exception e)` — fires when the socket closes (server shutdown, or client
  `/quit` triggers `try-with-resources` to close the socket). Prints the disconnection
  notice. `Exception` is used (rather than `IOException`) to catch any stream error.

```java
            // Send messages
            while (true) {
                String message = scanner.nextLine();
                out.writeUTF(message);

                if (message.equalsIgnoreCase("/quit")) {
                    break;
                }
            }
```
- `scanner.nextLine()` — **blocks** on keyboard input.
- `out.writeUTF(message)` — sends every typed line to the server immediately, **before**
  checking if it is `/quit`. This means the server receives the `/quit` string and
  `ClientHandler` handles the disconnect on the server side.
- `message.equalsIgnoreCase("/quit")` — checks after sending. Breaks the loop.
- After `break`, the `try-with-resources` block closes all four resources in reverse order.

```java
        } catch (Exception e) {
            e.printStackTrace();
        }
```
- Catches any `IOException` from the `try-with-resources` block (e.g., `ConnectException`
  "Connection refused" when the server is not running). `e.printStackTrace()` prints the
  full error chain to help diagnose connection problems.

---

## 7. Full Project Flow

### Phase 1: Server Starts

```
mvn exec:java -Dexec.mainClass="org.example.Server"
  │
  ▼
Server.main(args)
  │
  ▼
ServerLogger.serverStarted(5000)
  │  logs: "SERVER STARTED | Port: 5000 | Time: ..."
  │
  ▼
new ServerSocket(5000)
  │  OS: create socket → bind to port 5000 → start listening
  │
  ▼
serverSocket.accept()
  │
  └─ BLOCKS ─── main thread suspended, waiting for a client
```

**State:**
```
Server process:
  main thread  → BLOCKED on accept()
  clients list → []
  port 5000    → open, listening
```

---

### Phase 2: Client Connects

```
mvn exec:java -Dexec.mainClass="org.example.Client"
  │
  ▼
new Socket("localhost", 5000)
  │
  │  TCP Three-Way Handshake:
  │  client ──── SYN ────────────────► server
  │  client ◄─── SYN-ACK ─────────── server
  │  client ──── ACK ────────────────► server
  │
  ▼
socket connected

Server side: accept() unblocks, returns new Socket
  │
  ▼
ServerLogger.newClientConnected()
  │  logs: "NEW CLIENT CONNECTED | Time: ..."
  │
  ▼
new ClientHandler(socket)  →  handler created (thread not started)
clients.add(handler)       →  list: [ClientHandler(unnamed)]
handler.start()            →  NEW THREAD spawned
  │
  ▼
serverSocket.accept()      →  main thread BLOCKS again immediately
```

---

### Phase 3: Username Handshake

```
ClientHandler thread                    Client main thread
      │                                       │
      │  in = new DataInputStream(...)        │  in  = new DataInputStream(...)
      │  out = new DataOutputStream(...)      │  out = new DataOutputStream(...)
      │                                       │
      │  in.readUTF()  ← BLOCKS              │  scanner.nextLine() ← BLOCKS
      │                                       │  [user types "alice"]
      │                                       │
      │                                       │  out.writeUTF("alice")
      │                                       │  ────────── "alice" ──────────────►
      │  readUTF() returns "alice"            │
      │  username = "alice"                   │
      │  setName("ClientHandler-alice")       │
      │                                       │
      │  ServerLogger.userConnected(...)      │
      │  logs: "USER CONNECTED | alice | ..." │
      │                                       │
      │  Server.broadcast(                    │
      │    "alice joined the chat")           │
      │  ◄──── "alice joined the chat" ───────│  receive thread prints it
```

---

### Phase 4: Client Sends a Message

```
Client main thread               ClientHandler-alice          All clients
      │                                  │                        │
      │  scanner.nextLine()              │                        │
      │  [user types "hello"]            │                        │
      │                                  │                        │
      │  out.writeUTF("hello")           │                        │
      │  out.flush()                     │                        │
      │─────────── "hello" ─────────────►│                        │
      │                                  │  in.readUTF() returns  │
      │                                  │  message = "hello"     │
      │                                  │                        │
      │                                  │  not "/quit"           │
      │                                  │                        │
      │                                  │  Server.broadcast(     │
      │                                  │   "alice: hello")      │
      │                                  │  ServerLogger logs it  │
      │                                  │                        │
      │  ◄──── "alice: hello" ───────────│───────────────────────►│
      │  recv thread prints it           │      all recv threads print it
      │                                  │                        │
      │  scanner.nextLine() ← BLOCKS     │  in.readUTF() ← BLOCKS │
```

---

### Phase 5: Second Client Joins

```
bob connects → same Phase 2 + Phase 3 sequence

Server.broadcast("bob joined the chat")
  │
  ├── sendMessage to alice  ──► alice recv thread prints "bob joined the chat"
  └── sendMessage to bob    ──► bob recv thread prints "bob joined the chat"

State:
  clients list → [ClientHandler-alice, ClientHandler-bob]
  7 threads total:
    Server: main, ClientHandler-alice, ClientHandler-bob
    alice:  main, ReceiveThread-alice
    bob:    main, ReceiveThread-bob
```

---

### Phase 6: Clean Quit

```
alice types "/quit"

Client main thread               ClientHandler-alice          Other clients
      │                                  │                        │
      │  out.writeUTF("/quit")           │                        │
      │─────────── "/quit" ─────────────►│                        │
      │                                  │  in.readUTF() = "/quit"│
      │                                  │  equalsIgnoreCase match│
      │                                  │                        │
      │                                  │  Server.broadcast(     │
      │                                  │   "alice left the chat")
      │                                  │───────────────────────►│ others see it
      │                                  │                        │
      │                                  │  ServerLogger          │
      │                                  │  .userDisconnected()   │
      │                                  │  logs: "USER DISCONNECTED | alice"
      │                                  │                        │
      │                                  │  break → finally:      │
      │                                  │    removeClient(this)  │
      │                                  │    close() → socket.close()
      │                                  │  THREAD TERMINATES     │
      │                                  │                        │
      │  break send loop                 │                        │
      │  try-with-resources closes:      │                        │
      │    Scanner                       │                        │
      │    DataInputStream               │                        │
      │    DataOutputStream              │                        │
      │    Socket  ← TCP FIN sent        │                        │
      │  main thread exits               │                        │
      │  JVM: only daemon recv remains   │                        │
      │  JVM exits                       │                        │
      │                                  │                        │
      │  ReceiveThread-alice:            │                        │
      │  in.readUTF() → IOException      │                        │
      │  prints "[Disconnected from server]"
      │  THREAD TERMINATES (JVM already exiting)
```

---

### Phase 7: Unexpected Disconnect

```
bob's process crashes — no "/quit" sent

ClientHandler-bob thread
  │
  │  in.readUTF()  ← BLOCKED
  │
  │  TCP broken → SocketException / EOFException thrown
  │
  ▼
catch (Exception e):
  username != null → true
  ServerLogger.userDisconnectedUnexpectedly("bob")
  logs: "USER DISCONNECTED UNEXPECTEDLY | bob | ..."
  Server.broadcast("bob lost connection")
  │
  └── all remaining clients receive "bob lost connection"
  │
  ▼
finally:
  Server.removeClient(this)  → clients list: [alice]
  close()                    → socket.close()
  │
  ▼
ClientHandler-bob THREAD TERMINATES
```

---

## 8. Execution Order Table

Exact sequence of file and line executions from server start through a message send:

```
FILE               LINE    ACTION
────────────────────────────────────────────────────────────────────────────
Server.java         18     serverStarted(5000) logged
Server.java         21     new ServerSocket(5000) — port opens
Server.java         24     serverSocket.accept() — BLOCKS

Client.java         18     new Socket("localhost", 5000)
                           ── SYN ──────────────────────────────────────────►
                           ◄─ SYN-ACK ──────────────────────────────────────
                           ── ACK ──────────────────────────────────────────►

Server.java         24     accept() returns Socket
Server.java         25     newClientConnected() logged
Server.java         27     new ClientHandler(socket)
Server.java         28     clients.add(handler)
Server.java         29     handler.start() — NEW THREAD spawned
Server.java         24     accept() — BLOCKS again

ClientHandler.java  20     in  = new DataInputStream(...)
ClientHandler.java  21     out = new DataOutputStream(...)
Client.java         18     in  = new DataInputStream(...)
Client.java         19     out = new DataOutputStream(...)

Client.java         22     print "Enter your username: "
Client.java         23     scanner.nextLine() — BLOCKS (keyboard)
                           [user types "alice"]
ClientHandler.java  23     in.readUTF() — BLOCKS (network)

Client.java         24     out.writeUTF("alice") ─────────── "alice" ───────►
ClientHandler.java  23     readUTF() returns "alice"
ClientHandler.java  24     setName("ClientHandler-alice")
ClientHandler.java  25     userConnected("alice", "127.0.0.1") logged
ClientHandler.java  26     Server.broadcast("alice joined the chat")
Server.java         38     messageBroadcast logged
Server.java         39     loop: sendMessage to each client
ClientHandler.java  55     out.writeUTF("alice joined the chat") + flush
                           ◄──────────── "alice joined the chat" ────────────

Client.java         27-37  new Thread(lambda, "ReceiveThread-alice")
Client.java         38     setDaemon(true)
Client.java         39     receiveThread.start() — daemon thread starts
Client.java         31     in.readUTF() — BLOCKS (network, daemon)
Client.java         41     print "Connected as alice..."
Client.java         44     scanner.nextLine() — BLOCKS (keyboard)
ClientHandler.java  29     in.readUTF() — BLOCKS (network)

                           [user types "hello"]
Client.java         44     scanner.nextLine() returns "hello"
Client.java         45     out.writeUTF("hello") ─────────── "hello" ───────►
Client.java         46     out.flush()
ClientHandler.java  29     in.readUTF() returns "hello"
ClientHandler.java  31     equalsIgnoreCase("/quit") → false
ClientHandler.java  36     Server.broadcast("alice: hello")
Server.java         38     messageBroadcast logged
Server.java         39     loop: for each client
ClientHandler.java  55     out.writeUTF("alice: hello") + flush
                           ◄──────────── "alice: hello" ───────────────────
Client.java         31     in.readUTF() returns — recv thread unblocks
Client.java         32     System.out.println("alice: hello")
Client.java         31     in.readUTF() — BLOCKS again
ClientHandler.java  29     in.readUTF() — BLOCKS again
Client.java         44     scanner.nextLine() — BLOCKS again
────────────────────────────────────────────────────────────────────────────
```

---

## 9. Thread Map

Full thread state with alice and bob connected and idle:

```
JVM Process: Server
├── Thread: main                [BLOCKED: serverSocket.accept()]
├── Thread: ClientHandler-alice [BLOCKED: in.readUTF()]
└── Thread: ClientHandler-bob   [BLOCKED: in.readUTF()]

JVM Process: Client (alice)
├── Thread: main                [BLOCKED: scanner.nextLine()]
└── Thread: ReceiveThread-alice [BLOCKED: in.readUTF()]  (daemon)

JVM Process: Client (bob)
├── Thread: main                [BLOCKED: scanner.nextLine()]
└── Thread: ReceiveThread-bob   [BLOCKED: in.readUTF()]  (daemon)
```

**Total: 7 threads across 3 JVM processes.**
Each additional client adds: 1 server thread + 2 client-side threads.

| Event | Threads added | Threads removed |
|---|---|---|
| Server starts | 1 (server main) | — |
| Client connects | 1 (ClientHandler) + 2 (client main + recv) | — |
| Client quits | — | 1 (ClientHandler) + 2 (client main + recv) |

---

## 10. Logging Reference

All server-side logging goes through `ServerLogger`. No `System.out.println` on the server.

Logs are written to **two destinations simultaneously**:
- **Console** — the terminal where `mvn exec:java` is running.
- **`server_logs.txt`** — appended on every server start; created in the project root
  directory. Format: `LEVEL | MESSAGE | Time: ...`

Sample file content:
```
INFO | SERVER STARTED | Port: 5000 | Time: 2026-03-04T14:00:00.123
INFO | NEW CLIENT CONNECTED | Time: 2026-03-04T14:00:05.456
INFO | USER CONNECTED | Username: alice | IP: 127.0.0.1 | Time: ...
INFO | BROADCAST | Message: alice joined the chat | Time: ...
WARNING | USER DISCONNECTED UNEXPECTEDLY | Username: bob | Time: ...
INFO | SERVER STOPPED | Time: 2026-03-04T14:30:00.000
```

| Method | Level | When called | Sample output |
|---|---|---|---|
| `serverStarted(port)` | INFO | `Server.main()` before accept loop | `SERVER STARTED \| Port: 5000 \| Time: ...` |
| `serverStopped()` | INFO | `Server.finally` block | `SERVER STOPPED \| Time: ...` |
| `newClientConnected()` | INFO | `Server.main()` after `accept()` | `NEW CLIENT CONNECTED \| Time: ...` |
| `userConnected(user, ip)` | INFO | `ClientHandler.run()` after username read | `USER CONNECTED \| Username: alice \| IP: 127.0.0.1 \| Time: ...` |
| `userDisconnected(user)` | INFO | `ClientHandler.run()` on clean `/quit` | `USER DISCONNECTED \| Username: alice \| Time: ...` |
| `userDisconnectedUnexpectedly(user)` | WARNING | `ClientHandler.catch` block | `USER DISCONNECTED UNEXPECTEDLY \| Username: bob \| Time: ...` |
| `messageBroadcast(msg)` | INFO | `Server.broadcast()` | `BROADCAST \| Message: alice: hello \| Time: ...` |
| `privateMessage(from, to, text)` | INFO | `Server.sendPrivate()` | `PRIVATE MESSAGE \| From: alice \| To: bob \| Message: hey \| Time: ...` |
| `error(msg)` | SEVERE | Failed send, failed close, server exception | `ERROR: Failed to send message to alice: ...` |

---

## 11. Glossary

| Term | Definition |
|---|---|
| **Socket** | Software endpoint for a two-way TCP network connection |
| **ServerSocket** | Listens on a port; returns a new `Socket` per client via `accept()` |
| **Port** | 16-bit number (0–65535) identifying a service on a machine |
| **TCP** | Transmission Control Protocol — reliable, ordered, connection-oriented |
| **Three-way handshake** | SYN → SYN-ACK → ACK sequence that establishes a TCP connection |
| **`writeUTF`** | Writes a 2-byte length prefix followed by the UTF-8 bytes of a string |
| **`readUTF`** | Reads 2-byte length, then reads that many bytes, returns as a String |
| **`flush()`** | Pushes buffered bytes from the output stream into the TCP socket immediately |
| **Thread** | Smallest unit of execution; multiple threads run concurrently in one process |
| **Daemon thread** | Background thread; JVM exits when only daemon threads remain |
| **Blocking** | A call that pauses the thread until it can complete (e.g., waiting for data) |
| **`CopyOnWriteArrayList`** | Thread-safe list; copies internal array on every write |
| **Broadcast** | Sending a message to all connected clients simultaneously |
| **Username handshake** | Convention: first string the client sends is always the username |
| **try-with-resources** | Java syntax that auto-closes `AutoCloseable` resources on block exit |
| **Utility class** | Class with only static members and a private constructor; never instantiated |
| **`extends Thread`** | Makes the class itself a thread; `start()` calls the class's `run()` |
| **Lambda** | `() -> { ... }` — anonymous function; used here to implement `Runnable` |

---

## 12. Private Messaging

This section documents the **implemented** private message feature.

### 12.1 Syntax

A user sends a private message by prefixing their input with `@username`:

```
@bob hey, can you hear me?
```

- Only `bob` receives: `[PM from alice]: hey, can you hear me?`
- `alice` sees nothing extra — she already sees what she typed.
- All other connected clients see nothing.

---

### 12.2 Data Structure — `ConcurrentHashMap`

```java
// Server.java
public static final Map<String, ClientHandler> clientMap = new ConcurrentHashMap<>();
```

- **Key:** username string (e.g., `"alice"`).
- **Value:** the `ClientHandler` instance for that user.
- `ConcurrentHashMap` is fully thread-safe for concurrent `put`, `get`, and `remove`
  without external `synchronized` blocks. Multiple `ClientHandler` threads can call
  `clientMap.get(...)` simultaneously with no data corruption.

**Lifecycle of a map entry:**

```
Client connects
  │
  ▼
username = in.readUTF()          ← ClientHandler.run()
Server.registerClient(username, this)
  │  clientMap.put("alice", handler)
  │
  [alice is reachable by name]
  │
  ▼
Client disconnects (clean or crash)
  │
  ▼
finally: Server.removeClient(this)
  │  clients.remove(handler)
  │  clientMap.values().remove(handler)
  │
  [alice is no longer in the map]
```

---

### 12.3 Message Parser — `ClientHandler.java`

The receive loop now has three branches:

```java
while (true) {
    String message = in.readUTF();

    // Branch 1 — disconnect
    if (message.equalsIgnoreCase("/quit")) { ... break; }

    // Branch 2 — private message
    if (message.startsWith("@")) {
        int spaceIndex = message.indexOf(' ');
        if (spaceIndex > 1) {
            String targetUsername = message.substring(1, spaceIndex);
            String privateText    = message.substring(spaceIndex + 1);
            Server.sendPrivate(username, targetUsername, privateText);
        } else {
            sendMessage("[Server]: Usage: @username message");
        }
        continue;   // ← never reaches broadcast
    }

    // Branch 3 — normal broadcast
    Server.broadcast(username + ": " + message);
}
```

**Parser breakdown for `"@bob hey"`:**

```
message           →  "@bob hey"
indexOf(' ')      →  4
substring(1, 4)   →  "bob"     ← targetUsername
substring(5)      →  "hey"     ← privateText
```

**Validation — `spaceIndex > 1`:**

| Input | `indexOf(' ')` | `> 1` | Result |
|---|---|---|---|
| `"@bob hey"` | 4 | true | PM sent |
| `"@b hi"` | 2 | true | PM sent |
| `"@b"` | -1 | false | Usage hint |
| `"@ hi"` | 1 | false | Usage hint (empty username) |
| `"@"` | -1 | false | Usage hint |

---

### 12.4 `Server.sendPrivate()`

```java
public static void sendPrivate(String fromUsername, String toUsername, String text) {
    ClientHandler target = clientMap.get(toUsername);
    if (target == null) {
        ClientHandler sender = clientMap.get(fromUsername);
        if (sender != null) {
            sender.sendMessage("[Server]: User '" + toUsername + "' not found.");
        }
        return;
    }
    target.sendMessage("[PM from " + fromUsername + "]: " + text);
    ServerLogger.privateMessage(fromUsername, toUsername, text);
}
```

- `clientMap.get(toUsername)` — O(1) hash lookup. Returns `null` if the user is not
  connected.
- **Unknown target** — delivers an error only to the sender. No broadcast, no log.
- **Known target** — calls `target.sendMessage(...)` which writes directly to that
  client's `DataOutputStream`. No other client's handler is involved.
- Logs the PM at `INFO` level on the server.

---

### 12.5 Full Flow Diagram

```
alice types: "@bob hey"

Client (alice)             ClientHandler-alice         ClientHandler-bob
     │                            │                           │
     │  out.writeUTF("@bob hey")  │                           │
     │──────── "@bob hey" ───────►│                           │
     │                            │                           │
     │                      message.startsWith("@") → true    │
     │                      indexOf(' ') = 4                  │
     │                      targetUsername = "bob"            │
     │                      privateText = "hey"               │
     │                            │                           │
     │                      Server.sendPrivate(               │
     │                        "alice", "bob", "hey")          │
     │                            │                           │
     │                      clientMap.get("bob") → found      │
     │                            │                           │
     │                            │  target.sendMessage(      │
     │                            │   "[PM from alice]: hey") │
     │                            │──────────────────────────►│
     │                            │                 bob recv  │
     │                            │                 thread    │
     │                            │                 prints it │
     │                            │                           │
     │                      ServerLogger.privateMessage(...)  │
     │                      logs: "PRIVATE MESSAGE | From: alice | To: bob | ..."
     │                            │                           │
     │                      continue → in.readUTF() BLOCKS    │
```

**Unknown user scenario:**

```
alice types: "@charlie hey"

clientMap.get("charlie") → null
clientMap.get("alice")   → alice's handler
alice.sendMessage("[Server]: User 'charlie' not found.")
  ◄── "[Server]: User 'charlie' not found." ─── alice recv thread prints it
```

---

### 12.6 Edge Cases

| Scenario | Handling |
|---|---|
| `@unknownUser message` | Sender gets `[Server]: User 'unknownUser' not found.` |
| `@` with no username or text | `spaceIndex > 1` fails → sender gets usage hint |
| `@bob` with no message text | `spaceIndex > 1` fails → sender gets usage hint |
| Target disconnects mid-send | `clientMap.get()` returns `null` → "not found" error |
| Username case sensitivity | `ConcurrentHashMap` key is case-sensitive — `@Bob` ≠ `@bob` |
| Sender PMs themselves | Works — they receive their own `[PM from alice]: ...` |

---

### 12.7 Logging Reference (updated)

| Method | Level | When called |
|---|---|---|
| `messageBroadcast(msg)` | INFO | `Server.broadcast()` — every public message |
| `privateMessage(from, to, text)` | INFO | `Server.sendPrivate()` — every PM delivered |
