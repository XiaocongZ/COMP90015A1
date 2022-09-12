package comp90015.idxsrv.peer;

import comp90015.idxsrv.message.*;
import comp90015.idxsrv.textgui.ISharerGUI;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static java.lang.Thread.interrupted;

public class SharingThread implements Runnable{
    private ISharerGUI tgui;
    private Socket socket;

    public SharingThread(Socket socket, ISharerGUI tgui){
        this.tgui = tgui;
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            InputStream inputStream = socket.getInputStream();
            OutputStream outputStream = socket.getOutputStream();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));

            while (!interrupted()) {
                Message msg = readMsg(bufferedReader);
                if(msg.getClass() == BlockRequest.class){
                    BlockRequest bReq = (BlockRequest) msg;
                    //TODO reply
                } else if (msg.getClass() == Goodbye.class) {
                    break;
                }else {
                    throw new IOException("Message unidentified");
                }


            }
        }
        catch(JsonSerializationException e){
            Thread.currentThread().interrupt();
        } catch(IOException e){
            Thread.currentThread().interrupt();
        }

    }




    private void writeMsg(BufferedWriter bufferedWriter, Message msg) throws IOException {
        tgui.logDebug("sending: "+msg.toString());
        bufferedWriter.write(msg.toString());
        bufferedWriter.newLine();
        bufferedWriter.flush();
    }

    private Message readMsg(BufferedReader bufferedReader) throws IOException, JsonSerializationException {
        String jsonStr = bufferedReader.readLine();
        if(jsonStr!=null) {
            Message msg = (Message) MessageFactory.deserialize(jsonStr);
            tgui.logDebug("received: "+msg.toString());
            return msg;
        } else {
            throw new IOException();
        }
    }
}
