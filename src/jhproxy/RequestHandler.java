package jhproxy;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.StringTokenizer;

public class RequestHandler implements Runnable {
  protected DataInputStream clientInputStream;
  protected OutputStream clientOutputStream;
  protected OutputStream remoteOutputStream;
  protected InputStream remoteInputStream;
  protected Socket clientSocket;
  protected Socket remoteSocket;
  protected String requestType;
  protected String url;
  protected String uri;
  protected String httpVersion;
  protected HashMap<String, String> header;
  static String endOfLine = "\r\n";

  public RequestHandler(Socket clientSocket) {
    this.header = new HashMap<String, String>();
    this.clientSocket = clientSocket;
  }

  public void run() {
    try {
      this.clientInputStream = new DataInputStream(this.clientSocket.getInputStream());
      this.clientOutputStream = this.clientSocket.getOutputStream();

      clientToProxy();

      proxyToRemote();

      remoteToClient();

      System.out.println();
      if (this.remoteOutputStream != null) {
        this.remoteOutputStream.close();
      }
      if (this.remoteInputStream != null) {
        this.remoteInputStream.close();
      }
      if (this.remoteSocket != null) {
        this.remoteSocket.close();
      }
      if (this.clientOutputStream != null) {
        this.clientOutputStream.close();
      }
      if (this.clientInputStream != null) {
        this.clientInputStream.close();
      }
      if (this.clientSocket != null) {
        this.clientSocket.close();
      }
    } catch (IOException localIOException) {
    }
  }

  private void clientToProxy() {
    try {
      String line;
      if ((line = this.clientInputStream.readLine()) != null) {
        StringTokenizer tokens = new StringTokenizer(line);
        this.requestType = tokens.nextToken();
        this.url = tokens.nextToken();
        this.httpVersion = tokens.nextToken();
        
        System.out.println(line);
      }
      while ((line = this.clientInputStream.readLine()) != null) {
        if (line.trim().length() == 0) {
          break;
        }
        StringTokenizer tokens = new StringTokenizer(line);
        String key = tokens.nextToken(":");
        //String value = line.replaceAll(key, "").replace(": ", "");
        int i =  line.indexOf(':');
        String value = line.substring(i+1).trim();
        this.header.put(key.toLowerCase(), value);
        
        System.out.println(line);
      }
      stripUnwantedHeaders();
      getUri();
    } catch (UnknownHostException e) {
    } catch (SocketException e) {
    } catch (IOException e) {
    }
  }

  private void proxyToRemote() {
    try {
      if (this.header.get("host") == null) {
        return;
      }
      if ((!this.requestType.startsWith("GET")) && (!this.requestType.startsWith("POST"))) {
        return;
      }
      
      int port = 80;      
      StringTokenizer tokens = new StringTokenizer(this.header.get("host"), ":");
      String host = tokens.nextToken();
      if (tokens.hasMoreTokens()) {
        port = Integer.parseInt(tokens.nextToken());
      }
      
      
      this.remoteSocket = new Socket((String)host , port);
      this.remoteOutputStream = this.remoteSocket.getOutputStream();

      checkRemoteStreams();
      checkClientStreams();

      String request = this.requestType + " " + this.uri + " HTTP/1.0";
      this.remoteOutputStream.write(request.getBytes());
      this.remoteOutputStream.write(endOfLine.getBytes());
      System.out.println(request);

      String command = "host: " + (String) this.header.get("host");
      this.remoteOutputStream.write(command.getBytes());
      this.remoteOutputStream.write(endOfLine.getBytes());
      System.out.println(command);
      for (String key : this.header.keySet()) {
        if (!key.equals("host")) {
          command = key + ": " + (String) this.header.get(key);
          this.remoteOutputStream.write(command.getBytes());
          this.remoteOutputStream.write(endOfLine.getBytes());
          System.out.println(command);
        }
      }
      this.remoteOutputStream.write(endOfLine.getBytes());
      this.remoteOutputStream.flush();
      if (this.requestType.startsWith("POST")) {
        int contentLength = Integer.parseInt((String) this.header.get("content-length"));
        for (int i = 0; i < contentLength; i++) {
          this.remoteOutputStream.write(this.clientInputStream.read());
        }
      }
      this.remoteOutputStream.write(endOfLine.getBytes());
      this.remoteOutputStream.flush();
    } catch (UnknownHostException e) {
    } catch (SocketException e) {
    } catch (IOException e) {
    }
  }

  private void remoteToClient() {
    try {
      if (this.remoteSocket == null) {
        return;
      }
      DataInputStream remoteOutHeader = new DataInputStream(this.remoteSocket.getInputStream());
      String line;
      while ((line = remoteOutHeader.readLine()) != null) {
        if (line.trim().length() == 0) {
          break;
        }
        if ((!line.toLowerCase().startsWith("proxy")) && (!line.contains("keep-alive"))) {
          System.out.println(line);
          this.clientOutputStream.write(line.getBytes());
          this.clientOutputStream.write(endOfLine.getBytes());
        }
      }
      this.clientOutputStream.write(endOfLine.getBytes());
      this.clientOutputStream.flush();

      this.remoteInputStream = this.remoteSocket.getInputStream();
      byte[] buffer = new byte['?'];
      int i;
      while ((i = this.remoteInputStream.read(buffer)) != -1) {
        this.clientOutputStream.write(buffer, 0, i);
        this.clientOutputStream.flush();
      }
    } catch (UnknownHostException e) {
    } catch (SocketException e) {
    } catch (IOException e) {
    }
  }

  private void stripUnwantedHeaders() {
    if (this.header.containsKey("user-agent")) {
      this.header.remove("user-agent");
    }
    if (this.header.containsKey("referer")) {
      this.header.remove("referer");
    }
    if (this.header.containsKey("proxy-connection")) {
      this.header.remove("proxy-connection");
    }
    if ((this.header.containsKey("connection")) && (((String) this.header.get("connection")).equalsIgnoreCase("keep-alive"))) {
      this.header.remove("connection");
    }
  }

  private void checkClientStreams() {
    try {
      if (this.clientSocket.isOutputShutdown()) {
        this.clientOutputStream = this.clientSocket.getOutputStream();
      }
      if (this.clientSocket.isInputShutdown()) {
        this.clientInputStream = new DataInputStream(this.clientSocket.getInputStream());
      }
    } catch (UnknownHostException e) {
    } catch (SocketException e) {
    } catch (IOException e) {
    }
  }

  private void checkRemoteStreams() {
    try {
      if (this.remoteSocket.isOutputShutdown()) {
        this.remoteOutputStream = this.remoteSocket.getOutputStream();
      }
      if (this.remoteSocket.isInputShutdown()) {
        this.remoteInputStream = new DataInputStream(this.remoteSocket.getInputStream());
      }
    } catch (UnknownHostException e) {
    } catch (SocketException e) {
    } catch (IOException e) {
    }
  }

  private void getUri() {
    if (this.header.containsKey("host")) {
      int temp = this.url.indexOf((String) this.header.get("host"));
      temp += ((String) this.header.get("host")).length();
      if (temp < 0) {
        this.uri = this.url;
      } else {
        this.uri = this.url.substring(temp);
      }
    }
  }
}
