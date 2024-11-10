package com.ismail.dukascopy.strategy;


import com.dukascopy.api.*;
import com.dukascopy.api.IEngine.OrderCommand;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.*;


@RequiresFullAccess
public class SimpleSocketStrategy implements IStrategy {
    private IEngine engine;
    private IConsole console;
    private IContext context;
    private int counter = 0;
    private TransferSocketServer server;

    public void onStart(IContext context) throws JFException {
        this.engine = context.getEngine();
        this.console = context.getConsole();
        this.context = context;
        server = new TransferSocketServer();
    }

    public void onAccount(IAccount account) throws JFException {
    }

    public void onMessage(IMessage message) throws JFException {
    }

    public void onStop() throws JFException {
        for (IOrder order : engine.getOrders()) {
            engine.getOrder(order.getLabel()).close();
        }
        server.stopRun();
    }

    public void onTick(Instrument instrument, ITick tick) throws JFException {
    }

    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
    }

    protected String getLabel(Instrument instrument) {
        String label = instrument.name();
        label = label + (counter++);
        label = label.toUpperCase();
        return label;
    }

    public void print(String message) {
        console.getOut().println(message);
    }

    public class TransferSocketServer implements Runnable{
        private ServerSocket server;
        private Socket socket;
        private int port = 7001;
        private boolean serverRun = true;
        private Thread serverThread;

        public TransferSocketServer() {
            serverThread = new Thread(this, "Server");
            serverThread.start();
        }

        public void run() {
            try {
                server = new ServerSocket(port);
            } catch (IOException e) {
                e.printStackTrace(console.getErr());
            }
            handleConnection();
        }

        public void stopRun() {
            serverRun = false;
            try {
                server.close();
            } catch (IOException e) {
                e.printStackTrace(console.getErr());
            }
        }

        public void handleConnection() {
            System.out.println("Sever waiting for client and server message ");
            while (serverRun) {
                try {
                    socket = server.accept();
                    ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream());
                    ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream());

                    processMessageFromClient(inputStream, outputStream);

                    outputStream.close();
                    inputStream.close();
                    socket.close();
                } catch (SocketException e) {
                    if (!serverRun) {
                        System.out.println("Closing server");
                    } else {
                        e.printStackTrace(console.getErr());
                    }
                } catch (Exception e) {
                    e.printStackTrace(console.getErr());
                }
            }
        }

        private void processMessageFromClient(ObjectInputStream inputStream, ObjectOutputStream outputStream) throws IOException, ClassNotFoundException, InterruptedException, ExecutionException {
            String message = (String) inputStream.readObject();
            System.out.println("Message Received: " + message);
            Future<String> future = context.executeTask(new ClientTask(message));
            String string = future.get();
            if (string != null) {
                outputStream.writeObject("Connected. Command " +  string );
            } else {
                outputStream.writeObject("Connected. Incorrect command");
            }
        }
    }

    public class ClientTask implements Callable<String> {
        private String message;
        public ClientTask(String message) {
            this.message = message;
        }

        public String call() throws JFException {
            if (message.equals("BUY")){
                engine.submitOrder(getLabel(Instrument.EURUSD), Instrument.EURUSD, OrderCommand.BUY, 0.01);
                return "Buy order completed";
            }
            if (message.equals("SELL")) {
                engine.submitOrder(getLabel(Instrument.EURUSD), Instrument.EURUSD, OrderCommand.SELL, 0.01);
                return "Sell order completed";
            }
            return null;
        }
    }

}

