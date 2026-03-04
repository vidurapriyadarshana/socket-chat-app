# Socket Chat App — Simple Guide

A plain-language explanation of how this chat app works, focused on sockets and the flow of data.

---

## What is a Socket?

A socket is a two-way communication channel between two programs over a network.

Think of it like a phone call:
- One side dials (the client).
- The other side answers (the server).
- Both sides can talk and listen until one hangs up.

In Java, two classes handle this:

| Class | Role |
|---|---|
| `ServerSocket` | Opens a port and waits for incoming calls |
| `Socket` | The actual connection — has input and output streams |

---

## How the Connection is Made

### Server side

```java
ServerSocket serverSocket = new ServerSocket(5000); // open port 5000
Socket socket = serverSocket.accept();              // wait for a client
```

`accept()` **blocks** — the server thread sleeps here until a client connects.
When a client connects, `accept()` returns a `Socket` for that client.
The `ServerSocket` stays open and can accept more clients.

### Client side

```java
Socket socket = new Socket("localhost", 5000); // connect to the server
```

This one line does the TCP handshake:

```
Client                  Server
  |--- SYN ------------>|   "I want to connect"
  |<-- SYN-ACK ---------|   "OK"
  |--- ACK ------------>|   "Connected"
```

After this, both sides have a live socket and can send/receive data.

---

## How Messages are Sent

Both sides wrap the socket's streams with `DataInputStream` and `DataOutputStream`:

```java
DataOutputStream out = new DataOutputStream(socket.getOutputStream());
DataInputStream  in  = new DataInputStream(socket.getInputStream());
```

**Sending a string:**
```java
out.writeUTF("hello");
out.flush();
```

**Receiving a string:**
```java
String message = in.readUTF(); // blocks until a message arrives
```

`writeUTF` sends: a 2-byte length number, then the string bytes.
`readUTF` reads the 2-byte length first, then reads exactly that many bytes.
This is why the receiver always knows where one message ends and the next begins.

```
Wire format:
┌─────────────────┬─────────────────────────┐
│  2 bytes        │  N bytes                │
│  string length  │  the string (UTF-8)     │
└─────────────────┴─────────────────────────┘
```

`flush()` is required — without it, bytes may stay buffered and never be sent.

---

## Why Threads are Needed

Both reading from a socket and reading keyboard input are **blocking** — they
pause the program until data arrives. You cannot do both at the same time on
one thread.

This app uses multiple threads to solve that:

### Server — one thread per client

```
Server main thread:
  serverSocket.accept() → blocks, waiting for new clients

ClientHandler-alice thread (one per connected client):
  in.readUTF() → blocks, waiting for alice's next message
```

When a client connects, the server spawns a new `ClientHandler` thread:

```java
ClientHandler handler = new ClientHandler(socket);
clients.add(handler);
handler.start(); // spawns a new OS thread, calls handler.run()
```

`handler.start()` returns immediately. The main thread goes back to `accept()`.
The new thread runs `handler.run()` independently.

### Client — two threads

```
Client main thread:
  scanner.nextLine() → blocks on keyboard input, sends to server

Client receive thread:
  in.readUTF() → blocks on network, prints incoming messages
```

```java
new Thread(() -> {
    while (true) {
        String message = in.readUTF(); // blocks here
        System.out.println(message);
    }
}).start();
```

Without this second thread, the client could not receive messages while waiting
for the user to type.

---

## The First Message — Username Handshake

Before any chat happens, the client sends its username as the very first string.
The server reads it and uses it to identify this client.

```
Client                            Server (ClientHandler thread)
  |                                   |
  | out.writeUTF("alice")             |
  |---------------------------------->|
  |                             username = in.readUTF()
  |                             setName("ClientHandler-alice")
  |                             Server.registerClient("alice", this)
  |                             broadcast("alice joined the chat")
  |<-- "alice joined the chat" -------|
```

No special protocol — it is simply the first string the client writes.

---

## How a Message is Broadcast

When alice sends "hello":

```
alice (Client)         ClientHandler-alice        bob (Client)
      |                        |                       |
      | out.writeUTF("hello")  |                       |
      |----------------------->|                       |
      |                 in.readUTF() = "hello"         |
      |                        |                       |
      |                 Server.broadcast("alice: hello")
      |                        |                       |
      |<-- "alice: hello" -----|-------> bob receives  |
      |  echo filter skips it  |        bob prints it  |
```

`broadcast()` loops over all connected clients and calls `sendMessage()` on each:

```java
for (ClientHandler client : clients) {
    client.sendMessage(message); // writes to that client's DataOutputStream
}
```

Alice's own echo is filtered on the client side:

```java
if (message.startsWith(username + ": ")) {
    continue; // skip — alice already sees what she typed
}
```

---

## How Private Messaging Works

A user sends `@bob hey` to message only bob.

The server stores a map of username → handler:

```java
Map<String, ClientHandler> clientMap = new ConcurrentHashMap<>();
```

When `ClientHandler-alice` reads `@bob hey`:

```
message = "@bob hey"
indexOf(' ') = 4
substring(1, 4) = "bob"      ← who to send to
substring(5)    = "hey"      ← what to send

Server.sendPrivate("alice", "bob", "hey")
  clientMap.get("bob") → bob's ClientHandler
  bob.sendMessage("[PM from alice]: hey")
  ← only bob receives this
```

If `@unknownuser message` is sent:
```
clientMap.get("unknownuser") = null
alice.sendMessage("[Server]: User 'unknownuser' not found.")
← only alice sees the error
```

---

## How Disconnect Works

### Clean quit — user types `/quit`

```
Client sends "/quit"
  |
ClientHandler receives it
  → broadcasts "alice left the chat"
  → break out of receive loop
  → finally: removeClient(this) + socket.close()
  → thread terminates

Client main thread
  → out.writeUTF("/quit") already sent
  → break send loop
  → try-with-resources closes socket + streams
  → JVM exits
```

### Unexpected disconnect — crash or network drop

```
ClientHandler's in.readUTF() throws SocketException / EOFException
  → catch block: broadcast "alice lost connection"
  → finally: removeClient(this) + socket.close()
  → thread terminates
```

Either way, the `finally` block always runs and cleans up.

---

## Thread Safety — CopyOnWriteArrayList

`clients` is a `CopyOnWriteArrayList`, not a plain `ArrayList`.

Multiple `ClientHandler` threads all call `broadcast()` (which iterates the list)
and `removeClient()` (which modifies the list) at the same time. A plain `ArrayList`
would crash or corrupt in this situation.

`CopyOnWriteArrayList` is safe because:
- **Writing** (add/remove): makes a full copy of the array, modifies the copy, swaps it in.
- **Reading/iterating**: works on a stable snapshot, so removing a client mid-broadcast
  never causes an error.

```java
public static final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
```

`clientMap` uses `ConcurrentHashMap` for the same reason — it is safe for concurrent
`put`, `get`, and `remove` from multiple threads.

---

## How Logging Works

`ServerLogger` wraps Java's built-in `java.util.logging.Logger`.

When the class is first loaded, a `static {}` block runs once and sets up a
`FileHandler` that writes to `server_logs.txt`:

```java
static {
    FileHandler fileHandler = new FileHandler("server_logs.txt", true); // true = append
    fileHandler.setFormatter(...); // plain text: "INFO | message"
    logger.addHandler(fileHandler);
}
```

After this, every call like `logger.info(...)` goes to **both** the console and the file
automatically — no per-method file code needed.

---

## Complete Flow Summary

```
1. Server opens port 5000 → blocks on accept()

2. Client runs new Socket("localhost", 5000)
   → TCP handshake
   → accept() unblocks, server gets a Socket

3. Server creates ClientHandler(socket), adds to clients list, starts the thread
   → main thread immediately blocks on accept() again

4. ClientHandler reads first string → username
   → registers in clientMap
   → broadcasts "[name] joined the chat"

5. Client starts receive thread (blocks on in.readUTF())
   Client main thread blocks on scanner.nextLine()

6. User types a message → out.writeUTF(message)
   ClientHandler reads it → Server.broadcast("name: message")
   All clients receive it via their DataOutputStream

7. User types @bob text → private message
   ClientHandler parses it → Server.sendPrivate() → only bob receives it

8. User types /quit → ClientHandler breaks loop → finally cleans up
   Unexpected crash → catch logs it → finally cleans up
```

---

## Quick Reference

| Syntax | What it does |
|---|---|
| `new ServerSocket(5000)` | Opens port 5000 on the server |
| `serverSocket.accept()` | Blocks until a client connects; returns a Socket |
| `new Socket("localhost", 5000)` | Connects the client to the server |
| `socket.getInputStream()` | Raw byte stream coming in from the other side |
| `socket.getOutputStream()` | Raw byte stream going out to the other side |
| `out.writeUTF(text)` | Sends a string (2-byte length + UTF-8 bytes) |
| `in.readUTF()` | Receives a string; blocks until one arrives |
| `out.flush()` | Pushes buffered bytes into the network immediately |
| `socket.close()` | Ends the connection; unblocks the other side's readUTF |
| `handler.start()` | Spawns a new OS thread; calls handler.run() |
