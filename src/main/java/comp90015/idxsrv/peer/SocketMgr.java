package comp90015.idxsrv.peer;

import comp90015.idxsrv.message.JsonSerializationException;
import comp90015.idxsrv.message.Message;
import comp90015.idxsrv.message.MessageFactory;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class SocketMgr {
    private Socket socket;
    private BufferedReader bufferedReader;
    private BufferedWriter bufferedWriter;

    public SocketMgr(Socket socket) throws IOException {
        this.socket = socket;
        InputStream inputStream = socket.getInputStream();
        OutputStream outputStream = socket.getOutputStream();
        bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));

    }
    public void writeMsg(Message msg) throws IOException {
        bufferedWriter.write(msg.toString());
        bufferedWriter.newLine();
        bufferedWriter.flush();
    }

    public Message readMsg() throws IOException, JsonSerializationException {
        String jsonStr = bufferedReader.readLine();
        if(jsonStr!=null) {
            Message msg = (Message) MessageFactory.deserialize(jsonStr);
            return msg;
        } else {
            throw new IOException();
        }
    }

    public void close() throws IOException {
        socket.close();
    }
}
