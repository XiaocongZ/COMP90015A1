package comp90015.idxsrv.peer;


import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingDeque;

import comp90015.idxsrv.filemgr.FileMgr;
import comp90015.idxsrv.message.*;
import comp90015.idxsrv.server.IOThread;
import comp90015.idxsrv.server.IndexElement;
import comp90015.idxsrv.textgui.ISharerGUI;

import comp90015.idxsrv.filemgr.FileDescr;
import comp90015.idxsrv.textgui.PeerGUI;



/**
 * Skeleton Peer class to be completed for Project 1.
 * TODO close file
 * @author aaron
 *
 */
public class Peer implements IPeer {

	private IOThread ioThread;

	private Thread shareMgrThread;

	private LinkedBlockingDeque<Socket> incomingConnections;

	private ISharerGUI tgui;

	private String basedir;

	private int timeout;

	private int port;

	public Peer(int port, String basedir, int socketTimeout, ISharerGUI tgui) throws IOException {
		this.tgui=tgui;
		this.port=port;
		this.timeout=socketTimeout;
		this.basedir=new File(basedir).getCanonicalPath();
		incomingConnections = new LinkedBlockingDeque<>();
		ioThread = new IOThread(port,incomingConnections,socketTimeout,tgui);
		ioThread.start();

		shareMgrThread = new Thread(new ShareMgrThread(incomingConnections, (PeerGUI) tgui));
		shareMgrThread.start();

	}

	public void shutdown() throws InterruptedException, IOException {
		ioThread.shutdown();
		ioThread.interrupt();
		ioThread.join();

		shareMgrThread.interrupt();
		shareMgrThread.join();
	}

	/*
	 * Students are to implement the interface below.
	 */

	/**
	 * Send a {@link comp90015.idxsrv.message.ShareRequest} to index server.
	 * Set up {@link comp90015.idxsrv.server.IOThread} to listen to incoming {@link comp90015.idxsrv.message.BlockRequest}.
	 * Need to create a new thread for seeding.
	 * @param file The file to share index to server
	 * @param idxAddress Address of index server
	 * @param idxPort Listening port of index server
	 * @param idxSecret password for index server
	 * @param shareSecret password for the file
	 */
	@Override
	public void shareFileWithIdxServer(File file, InetAddress idxAddress, int idxPort, String idxSecret,
			String shareSecret) {
		try {
			String relativePath = getRelativePath(file.getCanonicalPath());
			FileMgr fileMgr = new FileMgr(relativePath);

			FileDescr fileDesc = fileMgr.getFileDescr();
			ShareRequest sReq = new ShareRequest(fileDesc, file.getName(), shareSecret, port);

			Socket socket = new Socket(idxAddress, idxPort);
			SocketMgr sMgr = new SocketMgr(socket);


			WelcomeMsg welcomeMsg = (WelcomeMsg) sMgr.readMsg();
			tgui.logInfo("Server welcome: " + welcomeMsg.toString());

			AuthenticateRequest aReq = new AuthenticateRequest(idxSecret);
			sMgr.writeMsg(aReq);
			AuthenticateReply aRep = (AuthenticateReply) sMgr.readMsg();
			if( !aRep.success){
				socket.close();
				throw new IOException("Authentication Failure");

			}
			else{
				sMgr.writeMsg(sReq);
				ShareReply sRep = (ShareReply) sMgr.readMsg();
				socket.close();

				ShareRecord sRec = new ShareRecord( fileMgr, sRep.numSharers, "Seeding", idxAddress, idxPort, idxSecret, shareSecret);

				tgui.logInfo("Share succeed; number of sharers: " + sRep.numSharers);
				//TODO make it relative path.
				tgui.addShareRecord(file.getPath(), sRec);

			}

		}
		catch (IOException e){
			tgui.logError("shareFileWithIdxServer: " + e.getMessage());
		}
		catch (NoSuchAlgorithmException e){
			tgui.logError("shareFileWithIdxServer: " + e.getMessage());
		}
		catch (JsonSerializationException e) {
			tgui.logError("shareFileWithIdxServer: " + e.getMessage());
		}
	}

	/**
	 * Send a {@link comp90015.idxsrv.message.SearchRequest} to index server.
	 * Parse the {@link comp90015.idxsrv.message.SearchReply}.
	 * @param keywords
	 * @param maxhits
	 * @param idxAddress
	 * @param idxPort
	 * @param idxSecret
	 */
	@Override
	public void searchIdxServer(String[] keywords,
			int maxhits,
			InetAddress idxAddress,
			int idxPort,
			String idxSecret) {

		try {
			Socket socket = new Socket(idxAddress, idxPort);
			InputStream inputStream = socket.getInputStream();
			OutputStream outputStream = socket.getOutputStream();
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
			BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));

			SearchRequest sReq = new SearchRequest(maxhits, keywords);

			WelcomeMsg welcomeMsg = (WelcomeMsg) readMsg(bufferedReader);
			tgui.logInfo("Server welcome: " + welcomeMsg.toString());

			AuthenticateRequest aReq = new AuthenticateRequest(idxSecret);
			writeMsg(bufferedWriter, aReq);
			AuthenticateReply aRep = (AuthenticateReply) readMsg(bufferedReader);
			if( !aRep.success){
				throw new IOException("Authentication Failure");
			}
			writeMsg(bufferedWriter, sReq);
			SearchReply sRep = (SearchReply) readMsg(bufferedReader);
			if (sRep.hits < 1){
				tgui.logInfo("No such file found.");
			} else {
				tgui.logInfo("Search success; numbers of seeders: " + sRep.seedCounts);
			}

		}
		catch (IOException e){
			tgui.logError("searchIdxServer: " + e.getMessage());
		}
		catch (NoSuchAlgorithmException e){
			tgui.logError("searchIdxServer: " + e.getMessage());
		} catch (JsonSerializationException e) {
			tgui.logError("searchIdxServer: " + e.getMessage());
		}

	}

	/**
	 * Send a {@link comp90015.idxsrv.message.DropShareRequest} to index server.
	 * Parse the {@link comp90015.idxsrv.message.DropShareReply}.
	 * Stop listening to incoming {@link comp90015.idxsrv.message.BlockRequest}.
	 * @param relativePathname the filename relative to the `basedir`
	 * @param shareRecord describes the shared file to drop
	 * @return boolean indicating if drop is successful
	 */
	@Override
	public boolean dropShareWithIdxServer(String relativePathname, ShareRecord shareRecord) {
		try {
			Socket socket = new Socket(shareRecord.idxSrvAddress, shareRecord.idxSrvPort);
			InputStream inputStream = socket.getInputStream();
			OutputStream outputStream = socket.getOutputStream();
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
			BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));

			DropShareRequest dSReq = new DropShareRequest(relativePathname, shareRecord.fileMgr.getFileDescr().getFileMd5(), shareRecord.sharerSecret, shareRecord.idxSrvPort);

			WelcomeMsg welcomeMsg = (WelcomeMsg) readMsg(bufferedReader);
			tgui.logInfo("Server welcome: " + welcomeMsg.toString());

			AuthenticateRequest aReq = new AuthenticateRequest(shareRecord.idxSrvSecret);
			writeMsg(bufferedWriter, aReq);
			AuthenticateReply aRep = (AuthenticateReply) readMsg(bufferedReader);
			if( !aRep.success){
				throw new IOException("Authentication Failure");
			}
			writeMsg(bufferedWriter, dSReq);
			DropShareReply dSRep = (DropShareReply) readMsg(bufferedReader);
			tgui.logInfo("Share dropped successfully");
		}
		catch (IOException e){
			tgui.logError("dropShareWithIdxServer: " + e.getMessage());
		}
		catch (NoSuchAlgorithmException e){
			tgui.logError("dropShareWithIdxServer: " + e.getMessage());
		} catch (JsonSerializationException e) {
			tgui.logError("dropShareWithIdxServer: " + e.getMessage());
		}
		return false;
	}

	/**
	 * Start a session.
	 * Send {@link comp90015.idxsrv.message.BlockRequest}.
	 * parse {@link comp90015.idxsrv.message.BlockReply}.
	 * Verify MD5 Hash value, store file block.
	 * End session.
	 * Manage concurrency, keep record of share and get.
	 * @param relativePathname
	 * @param searchRecord
	 */
	@Override
	public void downloadFromPeers(String relativePathname, SearchRecord searchRecord) {
		//tgui.logError("downloadFromPeers unimplemented");
		IndexElement[] indexElementArray = null;

		FileDescr fileDesc = null;
		try {
			fileDesc = searchRecord.fileDescr;
			LookupRequest lReq = new LookupRequest(relativePathname, fileDesc.getFileMd5());
			Socket socket = new Socket(searchRecord.idxSrvAddress, searchRecord.idxSrvPort);
			InputStream inputStream = socket.getInputStream();
			OutputStream outputStream = socket.getOutputStream();
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
			BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));

			writeMsg(bufferedWriter, lReq);
			LookupReply lRep = (LookupReply) readMsg(bufferedReader);

			indexElementArray = lRep.hits;

		} catch (IOException e) {
			tgui.logError("downloadFromPeers Lookup Failure: " + e.getMessage());
		} catch (JsonSerializationException e) {
			tgui.logError("downloadFromPeers Lookup Failure: " + e.getMessage());
		}

		if(indexElementArray == null || indexElementArray.length == 0){
			//TODO handle error
			return;
			//throw new IOException("Lookup Reply return 0 result");
		}

		//create FileMgr for file
		String filename = indexElementArray[0].filename;
		FileMgr fileMgr;
		try{
			fileMgr = new FileMgr(filename, fileDesc);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}


		//start threads to download from peers
		Thread downloadThread = new Thread(new DownloadThread(fileMgr, indexElementArray, (PeerGUI) tgui));
		//maybe no need to join.
		downloadThread.setDaemon(true);
		downloadThread.start();
	}


	/*
	 * Util methods for writing and reading messages.
	 * copied from server
	 */

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

	private String getRelativePath(String canonicalPath){
		Path pathAbsolute = Paths.get(canonicalPath);
		Path pathBase = Paths.get(basedir);
		Path pathRelative = pathBase.relativize(pathAbsolute);
		return pathRelative.toString();
	}

}
