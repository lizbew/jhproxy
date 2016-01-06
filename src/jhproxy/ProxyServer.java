package jhproxy;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;


public class ProxyServer {
  protected ServerSocket server;
  protected ExecutorService executor;
  protected static int LISTEN_PORT = 8080;

  public ProxyServer(int port) {
    this.executor = Executors.newCachedThreadPool();
    try {
      this.server = new ServerSocket(port);
    } catch (IOException localIOException) {
    }
  }

  public void accept() {
    try {
      while(true) {
        this.executor.execute(new RequestHandler(this.server.accept()));
      }
    } catch (IOException localIOException) {
    }
  }

  public static void main(String[] args) {
    int port = LISTEN_PORT;
    
    Options options = new Options();
    options.addOption("h", false, "Show Usage");
    options.addOption("p", true, "the port listen to");
    
    CommandLineParser parser = new DefaultParser();
    try {
      CommandLine cmd = parser.parse(options, args);
      if (cmd.hasOption("h")) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp( "ProxyServer -h | -p port", options );
        return;
      }
      if (cmd.hasOption("p")) {
        port = Integer.parseInt(cmd.getOptionValue("p"));
      }
      
    } catch (ParseException e) {
      e.printStackTrace();
    }
    
    System.out.println("ProxyServer is listening to port " + port);
    
    ProxyServer proxy = new ProxyServer(port);
    proxy.accept();
  }
}
