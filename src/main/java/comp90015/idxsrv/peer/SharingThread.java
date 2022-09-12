package comp90015.idxsrv.peer;

import comp90015.idxsrv.filemgr.BlockUnavailableException;
import comp90015.idxsrv.message.*;
import comp90015.idxsrv.textgui.PeerGUI;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static java.lang.Thread.interrupted;

public class SharingThread implements Runnable{
    private PeerGUI tgui;
    private Socket socket;

    public SharingThread(Socket socket, PeerGUI tgui){
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
                    ShareRecord sRec = tgui.getShareRecords().get(bReq.fileName);
                    if(sRec == null){
                        ErrorMsg eRep = new ErrorMsg("No share record");
                        writeMsg(bufferedWriter, eRep);
                        continue;
                    }
                    if(!sRec.status.equals("Seeding")){
                        ErrorMsg eRep = new ErrorMsg("Share Record Status not Seeding");
                        writeMsg(bufferedWriter, eRep);
                        continue;
                    }
                    String bytes = "";
                    try{
                        //TODO get block encode
                        sRec.fileMgr.readBlock(bReq.blockIdx);
                    }catch(BlockUnavailableException e){

                    }
                    BlockReply bRep = new BlockReply(bReq.fileName, bReq.fileMd5, bReq.blockIdx, bytes);
                    writeMsg(bufferedWriter, bRep);
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
