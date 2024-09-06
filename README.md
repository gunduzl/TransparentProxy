# Transparent Proxy


![alt text](image-2.png)


In basic terms, a proxy relays HTTP requests and responses back and forth between a client and a web server – see Figure 1. A transparent proxy, on the other hand, does the same thing without any configuration on the client’s part.
Figure 1: HTTP Proxy Basic Principle
This is achieved by modifying DNS records via a DNS server. For example, for the domain “www.google.com”, the DNS server should return its A record “172.217.17.100”. By using a specially-configured DNS server, the IP address can be modified to point to the IP of the transparent proxy. If this modification is done globally, then for every requested domain, the same IP of the transparent proxy can be set to be returned.

## Overview

Transparent Proxy is a Java-based application that acts as an HTTP/HTTPS proxy server, providing features like content filtering, caching, and logging. Built with JavaFX for the user interface, the proxy server is capable of handling requests, maintaining a resource cache, and allowing users to manage filtered hosts. The system uses token-based authentication to decide whether content filtering is enabled or disabled for each user. The application connects to a PostgreSQL database for logging requests and managing customer data.

## Features

- **HTTP/HTTPS Proxy**: Handles incoming HTTP and HTTPS requests, forwarding them to the destination and managing responses.
- **Token-based Authentication**: Requires users to authenticate using a token, which enables or disables content filtering.
- **Content Filtering**: Users can define which hosts should be blocked or allowed.
- **Caching**: Frequently accessed resources are cached to reduce load times and network traffic.
- **Logging**: All requests are logged to a PostgreSQL database for future analysis.
- **Graphical User Interface (GUI)**: Simple GUI using JavaFX to manage proxy operations, view logs, and configure filters.

## Project Structure

- **`TransparentProxy`**: Main class that starts the application and sets up the login screen.
- **`HomepageScreen`**: Manages the proxy server's start/stop functionality, log display, and user interaction.
- **`ServerHandler`**: Handles the actual request/response forwarding, caching, and content filtering.
- **`DatabaseConnection`**: Provides database connectivity to PostgreSQL for storing request logs and user data.

## How It Works

1. **User Login**: Users access the proxy through a login screen. They provide a token to either enable or disable content filtering.
2. **Proxy Operations**: Once logged in, users can start the proxy server to handle HTTP and HTTPS requests. The proxy checks whether a host is filtered or not before forwarding the request.
3. **Content Caching**: The proxy caches resources to improve performance and reduces repeated requests to the same server.
4. **Request Logging**: All requests made through the proxy are logged to the PostgreSQL database with details like IP, domain, and request type.

## Usage

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/your-repository.git
   ```
   
2. **Build and Run the Project**:
   You can run the project using a Java IDE like IntelliJ IDEA or build it using Maven/Gradle.

3. **Configure PostgreSQL**:
   Ensure that PostgreSQL is running on your system and is accessible. Update the connection parameters in the `DatabaseConnection` class.
   
   ```java
   private static final String DB_URL = "jdbc:postgresql://localhost:5432/proxy";
   private static final String USER = "postgres";
   private static final String PASSWORD = "your-password";
   ```

4. **Start the Proxy**:
   Launch the application and use the GUI to start/stop the proxy server. You can manage the filtered hosts and view logs through the interface.

## Dependencies

- Java 17
- JavaFX
- PostgreSQL
- Maven/Gradle for dependency management

## Diagram

The following diagram shows how requests are handled by the Transparent Proxy:

![alt text](image-1.png)



## License

This project is licensed under the MIT License - see the LICENSE file for details.
