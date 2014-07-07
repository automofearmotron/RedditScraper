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
	private JButton start;
	private JButton stop;
	private JTextField redditLocationField;
	private redditScraper rsScraper;
	private Thread activeThread;
	private RSEditorPane consolePane;
	private RSEditorPane deletePane;
	private redditScraper scraper; 
   private ArrayList<redditScraper> RSThreads = new ArrayList<redditScraper>();
	
   private int numThreads = 0;
	private String REDDIT_ROOT = "http://www.reddit.com/r/all/";
	
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
		c.weightx = 0.8;
		
		redditLocationField = new JTextField(100);
		redditLocationField.setText(REDDIT_ROOT);
		add(redditLocationField,c);
		
		c.gridx = 1;
		c.weightx = .1;
		
		start = new JButton("Start");
		start.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e)
			{
				startAThread();
			}
		});
		add(start,c);
		
		c.gridx = 2;
		
		stop = new JButton("Stop");
		stop.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e)
			{
				stopAThread();
			}
		});
		add(stop,c);
		//---------------------------------------------
		
		//---------------------------------------------
		c.gridx = 0;
		c.gridy = 1;
		c.weightx = 1;
		c.weighty = 1;
		c.gridwidth = 3;
		c.fill = GridBagConstraints.BOTH;
		
		JPanel leftPane = new JPanel();
		JPanel rightPane = new JPanel();
		consolePane = new RSEditorPane();
		consolePane.setContentType("text/html");
	   consolePane.setEditable(false);

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
      if(numThreads < 3)
      {
   		scraper = new redditScraper();
         RSThreads.add(scraper);
   		scraper.setRedditLocation(redditLocationField.getText());
   		scraper.setConsoleRSPane(consolePane);
   		scraper.setDeleteRSPane(deletePane);
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
}