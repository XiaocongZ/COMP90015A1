package comp90015.idxsrv.textgui;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TerminalTextUtils;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.Borders;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.DefaultWindowManager;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.EmptySpace;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.gui2.WindowListener;
import com.googlecode.lanterna.gui2.dialogs.FileDialogBuilder;
import com.googlecode.lanterna.gui2.dialogs.TextInputDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.gui2.table.Table;
import com.googlecode.lanterna.gui2.table.TableModel;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import com.googlecode.lanterna.terminal.TerminalResizeListener;

import comp90015.idxsrv.peer.IPeer;
import comp90015.idxsrv.peer.SearchRecord;
import comp90015.idxsrv.peer.ShareRecord;

/**
 * The PeerGUI provides a terminal interface for the Filesharer application.
 * @author aaron
 *
 */
public class PeerGUI implements ISharerGUI {
	private final int availabilityResolution = 1000;
	
	private final String INFOPREFIX = "Info";
	private final String WARNPREFIX = "Warn";
	private final String ERRORPREFIX = "Error";
	private final String DEBUGPREFIX = "Debug";
	
	private Terminal terminal;
	private Screen screen;
	private MultiWindowTextGUI gui;
	private BasicWindow mainWindow;
	private ConfigWindow configWindow;
	private TerminalSize ts;
	private ResizableTable<String> searchTable;
	private ResizableTable<String> sharingTable;
	private ResizableTable<TextLabel> msgTable;
	private TableModel<String> searchTableModel;
	private TableModel<String> sharingTableModel;
	private TableModel<TextLabel> msgTableModel;
	
	private final String[] searchTableHeaders= {"filename","sharers","size","MD5","IdxSrv"};
	private final String[] sharingTableHeaders= {"filename","sharers","status","blocks"};
	private final String[] msgTableHeaders = {"time","type","message"};

	private IPeer peer;
	
	private HashMap<String,ShareRecord> shareRecords;
	private HashMap<String,SearchRecord> searchRecords;
	
	private class KeyStrokeListener implements WindowListener {
		final PeerGUI pg;
		public KeyStrokeListener(PeerGUI pg){
			this.pg=pg;
		}
		
		@Override
		public void onInput(Window basePane, KeyStroke keyStroke, AtomicBoolean deliverEvent) {
			if(sharingTable.isFocused()) {
				if(keyStroke.getKeyType()==KeyType.Character) {
					if(keyStroke.getCharacter()=='d') {
						int selectedRow=sharingTable.getSelectedRow();
						String relativeName = sharingTable.getTableModel().getCell(0, selectedRow);
						pg.logDebug("Dropping "+relativeName);
						ShareRecord shareRecord = shareRecords.get(relativeName);
						boolean dropped=peer.dropShareWithIdxServer(relativeName, shareRecord);
						if(dropped) {
							sharingTable.getTableModel().removeRow(selectedRow);
							shareRecords.remove(relativeName);
							sharingTable.recomputeColumnMax();
							sharingTable.updateTableColumnPreferredSizes(ts.getColumns());
							if(sharingTable.getTableModel().getRowCount()==0) {
								sharingTable.setEnabled(false);
							}
						}
					}
				}
			} else if(searchTable.isFocused()) {
				if(keyStroke.getKeyType()==KeyType.Character) {
					if(keyStroke.getCharacter()=='d') {
						int selectedRow=searchTable.getSelectedRow();
						String relativeName = searchTable.getTableModel().getCell(0, selectedRow);
						String fileMd5 = searchTable.getTableModel().getCell(3, selectedRow);
						String key = relativeName+":"+fileMd5;
						pg.logDebug("Downloading "+relativeName+" with MD5 "+fileMd5);
						SearchRecord shareRecord = searchRecords.get(key);
						peer.downloadFromPeers(relativeName, shareRecord);
					}
				}
			} else {
				if(keyStroke.getKeyType()==KeyType.Character) {
					if(keyStroke.getCharacter()=='f') {
						handleFileShareButton();
					} else if(keyStroke.getCharacter()=='s') {
						handleSearchButton();
					} else if(keyStroke.getCharacter()=='c') {
						handleConfigButton();
					} else if(keyStroke.getCharacter()=='q') {
						handleQuitButton();
					} else if(keyStroke.getCharacter()=='h') {
						handleHelpButton();
					}
				}
			}
		}

		@Override
		public void onUnhandledInput(Window basePane, KeyStroke keyStroke, AtomicBoolean hasBeenHandled) {
			
		}

		@Override
		public void onResized(Window window, TerminalSize oldSize, TerminalSize newSize) {
			
		}

		@Override
		public void onMoved(Window window, TerminalPosition oldPosition, TerminalPosition newPosition) {
			
		}
	}
	
	/**
	 * Create a terminal based interface for the Filesharer application. The initializer
	 * will create the appropriate terminal graphics and may throw an IO exception if
	 * problems occur with the terminal. The separate method {@link setPeer} should be
	 * called to set which {@link comp90015.idxsrv.peer.IPeer} object this interface will make use of.
	 * @param idxSrvAddress the default index server address
	 * @param idxSrvPort the default index server port
	 * @param idxSrvSecret the default index server secret
	 * @param shareSecret the default sharing secret to use
	 * @throws IOException
	 */
	public PeerGUI(InetAddress idxSrvAddress,
			int idxSrvPort,
			String idxSrvSecret,
			String shareSecret) throws IOException {
		shareRecords = new HashMap<>();
		searchRecords = new HashMap<>();
		this.configWindow = new ConfigWindow(idxSrvAddress,
				idxSrvPort,
				idxSrvSecret,
				shareSecret,
				this);
		init();
	}
	
	/**
	 * Set the IPeer that this text GUI will make use of.
	 * @param peer
	 */
	public void setPeer(IPeer peer) {
		this.peer=peer;
	}
	
	/**
	 * Return a string no longer than width characters in length, by
	 * truncating the string and providing
	 * an ellipsis where needed/possible to indicate the string has
	 * been truncated.
	 * @param s the string
	 * @param width the maximum width
	 * @return the truncated string
	 */
	public static String fmtWidth(String s,int width) {
		if(s==null) return "";
		int length = TerminalTextUtils.getColumnWidth(s);
		if(width==0) return "";
		if(length<=width) return s;
		String ellipsis="...";
		if(width==5) {
			ellipsis="..";
		} else if(width==4) {
			ellipsis=".";
		} else if(width<=3) {
			ellipsis="";
		}
		int finalLength=Math.max(1, width-ellipsis.length());
		return s.substring(0,finalLength)+ellipsis;
	}
	
	/**
	 * Wrap the string at spaces by inserting new line characters,
	 * so that the maximum width of any line in the string is width.
	 * Words in the string are processed with {@link fmtWidth} so that
	 * under tight constraints long words are truncated. Contiguous spaces
	 * are replaced with at most a single space. The last line is never
	 * terminated with a newline character.
	 * @param s
	 * @param width
	 * @return the wrapped string
	 */
	public static String wordWrap(String s,int width) {
		if(s==null) return "";
		if(width==0) return "";
		String words[] = s.split(" ");
		String result="";
		int linelength=0;
		for(int i=0;i<words.length;i++) {
			String cword = fmtWidth(words[i],width);
			int cwordLength=TerminalTextUtils.getColumnWidth(cword);
			if(cwordLength==0) continue;
			if(linelength+cwordLength+(i>0?1:0)>width) {
				// start a new line
				result+="\n";
				result+=cword;
				linelength=cwordLength;
			} else {
				// stay on the same line
				if(i>0) {
					result+=" ";
					linelength++;
				}
				result+=cword;
				linelength+=cwordLength;
			}
		}
		return result;
	}
	
	/**
	 * Return a string of length width that is either "upsampled"
	 * from s using repeating characters or "downsampled" from s
	 * by skipping characters.
	 * @param s the given string
	 * @param width the width of the returned string
	 * @return the interpolated string
	 */
	public static String interpWidth(String s,int width) {
		double step = (double)s.length()/width;
		StringBuilder r = new StringBuilder();
		for(int i=0;i<width;i++) {
			int j = (int) Math.floor(step*i);
			r.append(s.charAt(j));
		}
		return r.toString();
	}
	
	/**
	 * Return an engineering string representation of the long value, with
	 * a given suffix.
	 * @param val
	 * @param suffix
	 * @return
	 */
	public String formatNumber(long val,String suffix) {
		if(val<1000) {
			return val+suffix;
		} else if(val<1000000) {
			return val/1000+"k"+suffix;
		} else if(val<1000000000) {
			return val/1000000+"M"+suffix;
		} else if(val<1000000000000L) {
			return val/1000000000+"G"+suffix;
		} else {
			return val/1000000000000L+"T"+suffix;
		}
	}
	
	private void handleFileShareButton() {
		File file = new FileDialogBuilder()
				.setTitle("Open File")
				.setDescription("Choose a file")
				.setActionLabel("Open")
				.build()
				.showDialog(gui);
		if(file!=null && peer!=null) {
			peer.shareFileWithIdxServer(file, 
				configWindow.getIdxSrvAddress(), 
				configWindow.getIdxSrvPort(), 
				configWindow.getIdxSrvSecret(), 
				configWindow.getShareSecret());
		}
	}
	
	private void handleSearchButton() {
		String input = TextInputDialog.showDialog(gui,
				"Keyword Search "+
						configWindow.getIdxSrvAddress().getHostName()+
						":"+configWindow.getIdxSrvPort(), 
						"Enter a space separated list of keywords", "");
		if(input!=null && peer!=null) {
			String[] keywords = input.split(" ");
			if(keywords.length>0) {
				peer.searchIdxServer(keywords, configWindow.getMaxhits(),
						configWindow.getIdxSrvAddress(),
						configWindow.getIdxSrvPort(),
						configWindow.getIdxSrvSecret());
			}
		}
	}
	
	private void handleConfigButton() {
		configWindow.prepareToBeActive();
		gui.setActiveWindow(configWindow);
	}
	
	private void handleHelpButton() {
		int staticWidth = (int)(ts.getColumns()*0.7); // if user resizes terminal, the word wrap will stay at same width
		MessageDialog.showMessageDialog(gui, "Help", 
				wordWrap("General keys\n============\n\n",staticWidth)+
				wordWrap("Use arrow keys and tab to navigate.\n",staticWidth)+
				wordWrap("f - share a file\n",staticWidth)+
				wordWrap("s - search for files\n",staticWidth)+
				wordWrap("c - configure settings\n",staticWidth)+
				wordWrap("h - open this help window\n",staticWidth)+
				wordWrap("q - quit the program\n",staticWidth)+
				wordWrap("\nSharing keys\n============\n\n",staticWidth)+
				wordWrap("d - drop the selected file from the index server (if possible) and stop downloading/uploading the file from/to other peers\n",staticWidth)+
				wordWrap("\nSearch keys\n===========\n\n",staticWidth)+
				wordWrap("d - download the selected search hit from other peers\n",staticWidth), MessageDialogButton.OK);
	}
	
	private void handleQuitButton() {
		mainWindow.close();
	}
	
	private void init() throws IOException {
		// get a reference to the terminal
	    terminal = new DefaultTerminalFactory().createTerminal();
	    // the terminal resize listener needs to update the preferred sizes for the tables
	    terminal.addResizeListener(new TerminalResizeListener() {
			@Override
			public void onResized(Terminal terminal, TerminalSize newSize) {
				// glitches can cause incorrect sizes, so we try to avoid them :-S
				if(newSize.getColumns()>0 && newSize.getRows()>0) {
					ts=newSize;
					int terminalWidth=ts.getColumns();
					if(searchTable!=null) {
						searchTable.updateTableHeaderPreferredSizes(terminalWidth);
					}
					if(sharingTable!=null) {
						sharingTable.updateTableHeaderPreferredSizes(terminalWidth);
					}
					if(msgTable!=null) {
						msgTable.updateTableHeaderPreferredSizes(terminalWidth);
					}
				}
			}
	    	
	    });
	    
	    // get a screen and get the terminal size
	    screen = new TerminalScreen(terminal);
	    ts=screen.getTerminalSize();
	    
	
	    /*
	     * Build the main window
	     */
	    
	    // Create a panel to hold the components
	    Panel panel = new Panel();
	    // Create a panel to hold the sharing table
	    Panel sharingPanel = new Panel();
	    sharingTable = new ResizableTable<String>(sharingTableHeaders);
	    sharingTable.setExpandableColumn(0);
	    sharingTable.setFixedColumn(3, 0.5);
	    sharingPanel.addComponent(sharingTable);
	    sharingTableModel = new TableModel<String>(sharingTableHeaders);
	    sharingTable.setTableModel(sharingTableModel);
	    sharingPanel.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Beginning,
	    		LinearLayout.GrowPolicy.CanGrow));
	    sharingTable.setCellRenderer(new ResizableCellRenderer<String>() {
	    	public void drawCell(Table<String> table, String cell, int columnIndex, int rowIndex,
	    			TextGUIGraphics textGUIGraphics) {
	    		if(columnIndex==3) {
	    			//System.out.println(cell.toString()+" "+textGUIGraphics.getSize().getColumns());
	    			textGUIGraphics.putString(0,0, PeerGUI.interpWidth(cell.toString(),textGUIGraphics.getSize().getColumns()));
	    		} else {
	    			textGUIGraphics.putString(0,0, PeerGUI.fmtWidth(cell.toString(),textGUIGraphics.getSize().getColumns()));
	    		}
	    	}
	    });
	    
	    
	    // Create a panel to hold the search table
	    Panel searchPanel = new Panel();
	    searchTable = new ResizableTable<String>(searchTableHeaders);
	    searchTable.setExpandableColumn(0);
	    searchPanel.addComponent(searchTable);
	    searchTableModel = new TableModel<String>(searchTableHeaders);
	    searchTable.setTableModel(searchTableModel);
	    searchPanel.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Beginning,
	    		LinearLayout.GrowPolicy.CanGrow));
	    
	    // Create a panel to hold the message table
	    Panel msgPanel = new Panel();
	    msgTable = new ResizableTable<TextLabel>(msgTableHeaders);
	    msgTable.setExpandableColumn(2);
	    msgPanel.addComponent(msgTable);
	    msgTableModel = new TableModel<TextLabel>(msgTableHeaders);
	    msgTable.setTableModel(msgTableModel);
	    msgPanel.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Beginning,
	    		LinearLayout.GrowPolicy.CanGrow));
	    
	    // Create a panel to hold the buttons
	    Panel buttonPanel = new Panel();
	    buttonPanel.setLayoutManager(new LinearLayout(Direction.HORIZONTAL));
	    
	    // Add the panels to the main panel
	    panel.addComponent(sharingPanel.withBorder(Borders.singleLine("Sharing")));
	    panel.addComponent(searchPanel.withBorder(Borders.singleLine("Search")));
	    panel.addComponent(msgPanel.withBorder(Borders.singleLine("Log")));
	    panel.addComponent(buttonPanel);
	
	    // Create window to hold the panel
	    mainWindow = new BasicWindow();
	    mainWindow.setComponent(panel);
	    mainWindow.setHints(Arrays.asList(Window.Hint.FULL_SCREEN, Window.Hint.NO_DECORATIONS));
	
	    KeyStrokeListener listener = new KeyStrokeListener(this);
	    mainWindow.addWindowListener(listener);
	    
	    // Create the gui
	    gui = new MultiWindowTextGUI(screen, new DefaultWindowManager(), new EmptySpace(TextColor.ANSI.BLUE));
	    
	    // add buttons to the button panel
	    
	    // share a file button
	    Button shareFileButton = new Button("File share", new Runnable() {
			@Override
			public void run() {
				handleFileShareButton();
			}
		});
	    buttonPanel.addComponent(shareFileButton);
	   
	    // search for a file button
	    buttonPanel.addComponent(new Button("Search", new Runnable() {
			@Override
			public void run() {
				handleSearchButton();
			}
	    	
	    }));
	    
	    // set config settings button
	    buttonPanel.addComponent(new Button("Config", new Runnable() {
			@Override
			public void run() {
				handleConfigButton();
			}
	    }));
	    
	    // set config settings button
	    buttonPanel.addComponent(new Button("Help", new Runnable() {
			@Override
			public void run() {
				handleHelpButton();
			}
	    }));
	    
	    // quit the application button
	    buttonPanel.addComponent(new Button("Quit", new Runnable() {
			@Override
			public void run() {
				handleQuitButton();
			}
	    }));
	    
	    // Focus starts with the share button
	    shareFileButton.takeFocus();
	}
	
	/**
	 * Set the main window to be the active window.
	 */
	public void setMainWindowActive() {
		gui.setActiveWindow(mainWindow);
	}
	
	/**
	 * Start the screen, add the windows, process user input (blocks),
	 * then stop the screen on exit.
	 * @throws IOException
	 */
	public void start() throws IOException {
		screen.startScreen();
		gui.addWindow(configWindow);
		gui.addWindowAndWait(mainWindow);
		screen.stopScreen();
	}
	
	@Override
	public void clearShareRecords() {
		sharingTable.clearTable();
		shareRecords.clear();
		sharingTable.invalidate();
		sharingTable.setEnabled(false);
	}
	
	private String createBlocks(boolean[] blockAvailability, int blocksColumnWidth) {
		String blocks="";
		for(int metablock=0;metablock<blocksColumnWidth;metablock++) {
			double start = metablock/blocksColumnWidth;
			double end = (metablock+1)/blocksColumnWidth;
			int bStart = (int)Math.floor(start*blockAvailability.length);
			int bEnd = (int)Math.ceil(end*blockAvailability.length);
			assert(bEnd>bStart);
			boolean complete=true;
			while(bStart<bEnd) {
				if(!blockAvailability[bStart]) {
					complete=false;
					break;
				}
				bStart+=1;
			}
			if(complete) {
				blocks+="X";
			} else {
				blocks+="-";
			}
		}
		return blocks;
	}
	
	@Override
	public void addShareRecord(String relativePathname, ShareRecord shareRecord) {
		if(shareRecords.containsKey(relativePathname)) {
			updateShareRecord(relativePathname, shareRecord);
		} else {
			shareRecords.put(relativePathname,shareRecord);
			sharingTableModel.addRow(
					relativePathname,
					formatNumber((long)shareRecord.numSharers,""),
					shareRecord.status,createBlocks(shareRecord.fileMgr.getBlockAvailability(),availabilityResolution));
			sharingTable.updateColumnMax();
			sharingTable.updateTableColumnPreferredSizes(ts.getColumns());
			sharingTable.setEnabled(true);
		}
	}
	
	@Override
	public void updateShareRecord(String relativePathname, ShareRecord shareRecord) {
		if(shareRecords.containsKey(relativePathname)) {
			TableModel<String> tm = sharingTable.getTableModel();
			for(int i=0;i<tm.getRowCount();i++) {
				if(tm.getCell(0, i).equals(relativePathname)){
					tm.setCell(1, i, formatNumber((long)shareRecord.numSharers,""));
					tm.setCell(2, i, shareRecord.status);
					tm.setCell(3, i, createBlocks(shareRecord.fileMgr.getBlockAvailability(),availabilityResolution));
					shareRecords.put(relativePathname,shareRecord);
					sharingTable.recomputeColumnMax();
					sharingTable.updateTableColumnPreferredSizes(ts.getColumns());
					break;
				}
			}
		} else {
			addShareRecord(relativePathname,shareRecord);
		}
	}
	
	@Override
	public void clearSearchHits() {
		searchTable.clearTable();
		searchRecords.clear();
		searchTable.invalidate();
		searchTable.setEnabled(false);
	}
	
	@Override
	public void addSearchHit(String relativePathname, SearchRecord searchRecord) {
		String key = relativePathname+":"+searchRecord.fileDescr.getFileMd5();
		if(searchRecords.containsKey(key)) {
			return;
		}
		searchTableModel.addRow(relativePathname,
				formatNumber(searchRecord.numSharers,""),
				formatNumber(searchRecord.fileDescr.getFileLength(),"b"),
				searchRecord.fileDescr.getFileMd5(),
				searchRecord.idxSrvAddress.getHostAddress()+":"+searchRecord.idxSrvPort);
		searchRecords.put(key,searchRecord);
		searchTable.updateColumnMax();
		searchTable.updateTableColumnPreferredSizes(ts.getColumns());
		searchTable.setEnabled(true);
	}

	private void addMsg(TextLabel prefix,TextLabel msg) {
		Calendar calendar = Calendar.getInstance();
		SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yy HH:mm:ss");
		TextLabel datetime = new TextLabel(formatter.format(calendar.getTime()));
		datetime.setForegroundColor(TextColor.ANSI.BLACK);
	    msgTableModel.addRow(datetime,prefix,msg);
	    msgTable.updateColumnMax();
	    msgTable.updateTableColumnPreferredSizes(ts.getColumns());
	    msgTable.setSelectedRow(msgTableModel.getRowCount());
	    msgTable.setEnabled(true);
	}
	
	@Override
	public void logInfo(String msg) {
		TextLabel prefix = new TextLabel(INFOPREFIX);
		prefix.setForegroundColor(TextColor.ANSI.BLUE_BRIGHT);
		TextLabel body = new TextLabel(msg);
		body.setForegroundColor(TextColor.ANSI.BLUE);
		addMsg(prefix,body);
	}

	@Override
	public void logWarn(String msg) {
		TextLabel prefix = new TextLabel(WARNPREFIX);
		prefix.setForegroundColor(TextColor.ANSI.YELLOW_BRIGHT);
		TextLabel body = new TextLabel(msg);
		body.setForegroundColor(TextColor.ANSI.YELLOW);
		addMsg(prefix,body);
	}

	@Override
	public void logError(String msg) {
		TextLabel prefix = new TextLabel(ERRORPREFIX);
		prefix.setForegroundColor(TextColor.ANSI.RED_BRIGHT);
		TextLabel body = new TextLabel(msg);
		body.setForegroundColor(TextColor.ANSI.RED);
		addMsg(prefix,body);
	}

	@Override
	public void logDebug(String msg) {
		TextLabel prefix = new TextLabel(DEBUGPREFIX);
		prefix.setForegroundColor(TextColor.ANSI.BLACK_BRIGHT);
		TextLabel body = new TextLabel(msg);
		body.setForegroundColor(TextColor.ANSI.BLACK);
		addMsg(prefix,body);
	}

	

	
}
