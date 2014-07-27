/*
   GUI for Reddit Scraper
*/

import javax.swing.*;
import java.util.*;
import javax.swing.UIManager.*;
import javax.swing.event.*;
import javax.swing.plaf.basic.*;
import java.awt.*;
import java.awt.event.*;
import java.net.URL;
import java.net.URLConnection;
import java.net.URI;

public class RSControlPanel extends JFrame
{
	private int WIDTH;
	private int HEIGHT;
	private boolean hot;
	private boolean fresh;
	private boolean all;
	private boolean html;
	private boolean api;
	private JButton start;
	private JButton stop;
   private JButton clearConsole;
	private JRadioButton hotButton;
	private JRadioButton freshButton;
	private JRadioButton htmlButton;
	private JRadioButton apiButton;
	private JCheckBox allCheckBox;
	private JTextField redditLocationField;
	private JTextField subredditField;
	private redditScraper rsScraper;
	private Thread activeThread;
	private RSEditorPane consolePane;
	private RSEditorPane deletePane;
	private redditScraper scraper; 
   private ArrayList<redditScraper> RSThreads = new ArrayList<redditScraper>();
	
   private int numThreads = 0;
	private String REDDIT_ROOT = "all";
	
	public RSControlPanel()
	{
		
		try {
	    for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
	        if ("Nimbus".equals(info.getName())) {
	            UIManager.setLookAndFeel(info.getClassName());
	            break;
	        }
	    }
		} catch (Exception e) {
		   
		}
      WindowAdapter windowAdapter = new WindowAdapter()
      {
         public void windowClosing(WindowEvent we)
         {
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
         }
      };
      addWindowListener(windowAdapter);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		// The basic frame construction settings
		setTitle("Reddit Scraper");
		Toolkit toolkit =  Toolkit.getDefaultToolkit ();
 		Dimension dim = toolkit.getScreenSize();
		WIDTH = dim.width / 2;
		HEIGHT = dim.height / 2;
		setBounds(0, 0,WIDTH,HEIGHT);
		
		//Layout
		setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.FIRST_LINE_END;
		c.gridx = 0;
		c.gridy = 0;
		c.weightx = 0.75;
		
		
		redditLocationField = new JTextField(50);
		redditLocationField.setText(REDDIT_ROOT);
		add(redditLocationField,c);
		
		c.gridx = 1;
		c.weightx = .75;
		
		subredditField = new JTextField(50);
		subredditField.setText("Set Subreddit Destination");
		add(subredditField,c);
		
		c.weightx = .10;
		c.gridx = 2;
		
		start = new JButton("Start");
		start.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e)
			{
				startAThread();
			}
		});
		add(start,c);
		
		c.gridx = 3;
		
		stop = new JButton("Stop");
		stop.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e)
			{
				stopAThread();
			}
		});
		add(stop,c);
      
      c.gridx=4;
      
      clearConsole = new JButton("Clear Console");
		clearConsole.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e)
			{
				consolePane.setBody("");
			}
		});
      
		add(clearConsole,c);
		//---------------------------------------------
		
		c.gridx= 0;
		c.gridy = 1;
		c.weightx = .75;
		c.weighty = 0;
		c.gridwidth = 1;
		c.fill = GridBagConstraints.BOTH;
		c.anchor = GridBagConstraints.LINE_START;
		
		hotButton = new JRadioButton("HOT");
		hotButton.setSelected(true);
		
		freshButton = new JRadioButton("NEW");
		htmlButton = new JRadioButton("HTML");
		apiButton = new JRadioButton("API");
		allCheckBox = new JCheckBox("r/all");
		allCheckBox.setSelected(false);
		
		hotButton.addItemListener(new guiButtonListener());
		freshButton.addItemListener(new guiButtonListener());
		htmlButton.addItemListener(new guiButtonListener());
		apiButton.addItemListener(new guiButtonListener());
		allCheckBox.addItemListener(new guiButtonListener());
		allCheckBox.setSelected(true);
		
		ButtonGroup popularityType = new ButtonGroup();
		popularityType.add(hotButton);
		popularityType.add(freshButton);
		add(hotButton,c);
		c.gridx=1;
		add(freshButton,c);
		c.gridx=2;
		ButtonGroup requestType = new ButtonGroup();
		requestType.add(htmlButton);
		requestType.add(apiButton);
		add(htmlButton,c);
		c.gridx=3;
		add(apiButton,c);
		c.gridx=4;
		add(allCheckBox,c);
		
		
		
		//---------------------------------------------
		c.gridx = 0;
		c.gridy = 2;
		c.weightx = 1;
		c.weighty = 1;
		c.gridwidth = 5;
		c.fill = GridBagConstraints.BOTH;
		
		JPanel leftPane = new JPanel();
		JPanel rightPane = new JPanel();
		consolePane = new RSEditorPane();
		consolePane.setContentType("text/html");
	   consolePane.setEditable(false);
	   consolePane.addHyperlinkListener(new HyperlinkListener() {
           @Override
           public void hyperlinkUpdate(HyperlinkEvent hle) {
			  		System.out.println("Description: " + hle.getDescription() +
											 "Event Type: " + hle.getEventType());
               if (HyperlinkEvent.EventType.ACTIVATED.equals(hle.getEventType())) {
                   System.out.println("LINK CLICKED" + hle.getURL());
                   Desktop desktop = Desktop.getDesktop();
                   try {
						 	String url = hle.getDescription();
						 	 String httpHeader = "";
							 if(url.contains("http://") == false && url.contains("www.") == false)
							 {
							 	httpHeader = "http://";
							 }
                      String URIConversion = httpHeader + hle.getDescription();
                      URIConversion = URIConversion.trim();
						 	 URI website = new URI(URIConversion);
                      desktop.browse(website);
                   } catch (Exception ex) {
                       ex.printStackTrace();
                   }
               }
           }
       });
		deletePane = new RSEditorPane();
		deletePane.setContentType("text/html");
	   deletePane.setEditable(false);
	   deletePane.addHyperlinkListener(new HyperlinkListener() {
           @Override
           public void hyperlinkUpdate(HyperlinkEvent hle) {
			  		System.out.println("Description: " + hle.getDescription() +
											 "Event Type: " + hle.getEventType());
               if (HyperlinkEvent.EventType.ACTIVATED.equals(hle.getEventType())) {
                   System.out.println("LINK CLICKED" + hle.getURL());
                   Desktop desktop = Desktop.getDesktop();
                   try {
						 	String url = hle.getDescription();
						 	 String httpHeader = "";
							 if(url.contains("http://") == false && url.contains("www.") == false)
							 {
							 	httpHeader = "http://";
							 }
                      String URIConversion = httpHeader + hle.getDescription();
                      URIConversion = URIConversion.trim();
						 	 URI website = new URI(URIConversion);
                      desktop.browse(website);
                   } catch (Exception ex) {
                       ex.printStackTrace();
                   }
               }
           }
       });
		JSplitPane split = new JSplitPane();
		JScrollPane sp = new JScrollPane(consolePane);
		split.setLeftComponent(sp);
		JScrollPane sp2 = new JScrollPane(deletePane);
		split.setRightComponent(sp2);
		add(split,c);
		setVisible(true);
	}
	
	public void startAThread()
	{
      if(numThreads <= 4)
      {
   		scraper = new redditScraper();
         RSThreads.add(scraper);
   		scraper.setRedditLocation(redditLocationField.getText());
			scraper.setSubmitSubreddit(subredditField.getText());
   		scraper.setConsoleRSPane(consolePane);
   		scraper.setDeleteRSPane(deletePane);
			scraper.setUseAPIRequests(api);
			scraper.setSearchTopPosts(hot);
			scraper.setIsSubredditAll(all);
   		activeThread = new Thread(scraper);
   		activeThread.start();
         numThreads++;
      }
		
	}
	
	public void stopAThread()
	{
		try{
         for(int i = 0;i < RSThreads.size();i++)
         {
			   RSThreads.get(i).setWatching(false);
			   consolePane.append("Stopping thread # " + i + ". Threads must run their course before stopping");
         }
		}catch(Exception e){
		
		}
	}
	public static void main(String[] args)
	{
		RSControlPanel visuals = new RSControlPanel();
	}
	
	public class guiButtonListener implements ItemListener
	{
		public void itemStateChanged(ItemEvent e)
		{
			Object source = e.getItemSelectable();
			
			if(source == api)
			{
				api = true;
				html = false;
			}else if(source == html){
				html = true;
				api = false;
			}else if(source == freshButton){
				fresh = true;
				hot = false;
				api = true;
				html = false;
				allCheckBox.setSelected(false);
				apiButton.setSelected(true);
			}else if(source == hotButton){
				hot = true;
				fresh = false;
			}else if(source == allCheckBox)
			{
				if(e.getStateChange() == ItemEvent.SELECTED) {
					all = true;
					html = true;
					api = false;
					fresh = false;
					htmlButton.setSelected(true);
        		}else{
					all = false;
					
				}
				
			}
		}
	}
}