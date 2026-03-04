# AGENTS.md — Socket Chat App

Developer guide for agentic coding agents operating in this repository.

---

## Project Overview

A TCP socket-based chat application written in **Java 21** using the Maven build system.
The architecture is a thread-per-client server that serializes `Message` objects over
`ObjectOutputStream`/`ObjectInputStream`. There are no external dependencies; the entire
project relies on the Java 21 standard library.

- **Group ID:** `org.example`
- **Artifact ID:** `Socket-Chat-App`
- **Java version:** 21 (source and target)
- **Encoding:** UTF-8

---

## Build Commands

All build commands use a locally installed Maven installation.
Run all commands from the repository root.

```bash
# Compile the project
mvn compile

# Package into a JAR (skipping tests since none exist yet)
mvn package -DskipTests

# Clean build artifacts
mvn clean

# Clean and recompile
mvn clean compile

# Clean and package
mvn clean package -DskipTests

# Run all tests (currently none; will use JUnit when tests are added)
mvn test

# Run a single test class (replace with actual class name)
mvn test -Dtest=MyTestClassName

# Run a single test method within a class
mvn test -Dtest=MyTestClassName#myMethodName
```

Build output is written to `target/`. The compiled classes land in
`target/classes/org/example/`.

---

## Running the Application

The project has **two separate entry points** that must be started in order:

```bash
# 1. Start the server (listens on port 5000)
mvn exec:java -Dexec.mainClass="org.example.Server"

# 2. Start one or more clients (connects to localhost:5000)
mvn exec:java -Dexec.mainClass="org.example.Client"
```

The client accepts keyboard input. Type `/logout` to disconnect gracefully.

IntelliJ run configurations named **Server**, **Client**, and **Main** are stored in
`.idea/workspace.xml`.

---

## Source Layout

```
src/
  main/
    java/org/example/
      Client.java         # Chat client entry point; /logout command to exit
      ClientHandler.java  # Per-client server-side thread
      Message.java        # Serializable wire message model (CHAT / LOGOUT types)
      Server.java         # Server entry point, broadcasts messages
      ServerLogger.java   # Logging utility (java.util.logging)
    resources/            # Empty
  test/
    java/                 # Empty — no tests yet
```

---

## Adding Tests

No test framework is currently declared. When adding tests, add JUnit 5 to `pom.xml`:

```xml
<dependencies>
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>5.11.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <version>3.2.5</version>
        </plugin>
    </plugins>
</build>
```

Place test classes under `src/test/java/org/example/` mirroring the main source tree.
Use the naming convention `<ClassName>Test.java` (e.g., `MessageTest.java`).

---

## Code Style Guidelines

### Package & File Structure

- Single package: `org.example`. Do not introduce sub-packages without discussion.
- One top-level public class per file; the file name must match the class name exactly.
- `package org.example;` is always the first non-blank line.

### Imports

- Import each class individually — **no wildcard imports**.
- Use only `java.*` standard library imports. No third-party libraries are declared.
- Order: standard library imports grouped together, no blank lines between them.

```java
// Correct
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

// Wrong
import java.io.*;
import java.net.*;
```

### Naming Conventions

| Element | Style | Example |
|---|---|---|
| Classes | `PascalCase` | `ClientHandler`, `ServerLogger` |
| Methods | `camelCase` | `startServer()`, `sendMessage()` |
| Fields | `camelCase` | `clientName`, `outputStream` |
| Constants | `SCREAMING_SNAKE_CASE` | `private static final int PORT = 5000` |
| Local variables | `camelCase` | `socket`, `handler` |
| Enum values | `SCREAMING_SNAKE_CASE` | `Message.Type.CHAT`, `Message.Type.LOGOUT` |

### Formatting

- **Indentation:** 4 spaces (no tabs).
- **Braces:** Opening brace on the same line as the declaration.
- **No trailing whitespace.**
- **Encoding:** UTF-8 for all source files.

```java
// Correct brace style
public void sendMessage(Message message) {
    try {
        outputStream.writeObject(message);
    } catch (Exception e) {
        ServerLogger.error("Failed to send to " + clientName + ": " + e.getMessage());
    }
}
```

### Types & Class Design

- Prefer `private final` fields; only drop `final` when the field must be assigned
  after construction (e.g., streams opened inside `run()`).
- Use `implements Serializable` on any class sent over a socket stream.
  Always declare `private static final long serialVersionUID = 1L;`.
- Static utility classes (`Server`, `ServerLogger`) have only `static` members
  and a `private` no-arg constructor to prevent instantiation.
- Thread classes extend `Thread` directly (existing convention); use `@Override` on
  `run()`. Always set a meaningful thread name via `setName(...)` or the
  `Thread(Runnable, String)` constructor.
- Mark daemon threads with `setDaemon(true)` when they should not block JVM shutdown
  (e.g., the client's receive thread).
- Use `Message.Type` enum to distinguish message kinds; never parse message text
  to determine control flow.

### Error Handling

- Use **try-with-resources** for anything that implements `AutoCloseable`
  (`Socket`, `ServerSocket`, `Scanner`, streams).
- Log errors via `ServerLogger.error(...)` rather than bare `System.out.println`
  or `e.printStackTrace()` on the server side.
- Do not swallow exceptions silently without a clear justification. An empty
  `catch` requires an explanatory comment.
- Avoid rethrowing checked exceptions as unchecked `RuntimeException` in production
  paths; prefer logging and graceful shutdown.
- Always call `Server.removeClient(this)` and `close()` in a `finally` block when a
  `ClientHandler` exits so the client list and socket stay consistent.
- Distinguish between a clean logout (`Message.Type.LOGOUT`) and an unexpected
  connection drop (`catch` block) so each path logs correctly.

```java
// Preferred pattern for ClientHandler
} catch (Exception e) {
    ServerLogger.userDisconnected(clientName);
    Server.broadcast(new Message("Server", clientName + " lost connection"));
} finally {
    Server.removeClient(this);
    close();
}
```

### Logging

Use `ServerLogger` for all server-side structured log output; never use
`System.out.println` on the server side. The underlying logger is
`java.util.logging.Logger` named `"ChatServer"`.

Available methods:
- `ServerLogger.serverStarted(int port)`
- `ServerLogger.serverStopped()`
- `ServerLogger.userConnected(String username, String ip)`
- `ServerLogger.userDisconnected(String username)`
- `ServerLogger.messageBroadcast(String sender, String content)`
- `ServerLogger.error(String message)`

Call `userConnected` right after the username handshake completes in `ClientHandler`.
Call `userDisconnected` on both clean logout and unexpected disconnect.
Call `messageBroadcast` inside `Server.broadcast()` before iterating clients.

### Concurrency

- `Server.clients` is a `CopyOnWriteArrayList` — safe for concurrent reads and
  infrequent writes from multiple `ClientHandler` threads. Do not replace it with
  a plain `ArrayList`.
- Always give threads a meaningful name (e.g., `"ClientHandler-alice"`,
  `"ReceiveThread-bob"`) to aid debugging.

---

## Logout Flow

1. Client user types `/logout`.
2. `Client.java` sends a `Message` with `type = Message.Type.LOGOUT`.
3. `ClientHandler.run()` detects the `LOGOUT` type, broadcasts a leave notice,
   calls `ServerLogger.userDisconnected`, then breaks out of the receive loop.
4. The `finally` block calls `Server.removeClient(this)` and `close()`.

Unexpected disconnections (I/O exception) are handled in the `catch` block with
the same cleanup, but log a "lost connection" notice instead of "left the chat".

---

## No Linting or Formatting Tooling

There is no Checkstyle, SpotBugs, PMD, or formatter plugin configured. Follow the
style conventions in this document manually. When adding a build plugin, add it
inside a `<build><plugins>` block in `pom.xml`.
