/*
	The Reddit Scraper scours defined subreddits for deleted submissions. It uses a combination of regex'ing through
	html and posting it using a reddit API wrapper. It's not the most efficient or best practice coding, but I've
	only had a chance to write it while at work, with short intervals.  Also going to pre-apologize for using
	so many generic exceptions.  I'll make them more defined later. I also didn't use File pathSeparator, so right now
	it will only write files on Windows correctly. I think. You can always comment out the file writing anyway, it's
	only meant to create a reference of found links against ones posted in case there's a post failure by the bot.
	If you have any questions about the code or wish to add features (I will be adding more than just scraping) let me know.
   
	/u/prototypexx
	
	p.s.
	Lot's of thanks to a guy named Karan who had a nifty Java API wrapper on Github. Saved me a lot of time.
*/import javax.swing.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;
import java.util.concurrent.TimeUnit;

import com.github.jreddit.user.User;
import com.github.jreddit.captcha.*;
import com.github.jreddit.utils.restclient.HttpRestClient;
import com.github.jreddit.utils.restclient.RestClient;
import com.github.jreddit.submissions.*;
import com.github.jreddit.comment.*;

import org.apache.commons.net.PrintCommandListener;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPHTTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPConnectionClosedException;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;
import org.apache.commons.net.io.CopyStreamEvent;
import org.apache.commons.net.io.CopyStreamListener;
import org.apache.commons.net.util.TrustManagerUtils;
import org.apache.commons.lang3.StringEscapeUtils;

public class redditScraper implements Runnable
{
	private String nextButton = "<span class=\"nextprev\">";
	private String titleFinder = "<p class=\"title\">";
	private String tabIndexFinder = "tabindex=\"1\" >";
	private String nextPage = "";
	private String redditLocation = "";
	private String redditRoot = "https://www.reddit.com/r/";
	private String userName = "";
	private String password = "";
	private String submitSubreddit = "";
	private String listItem = "<li class=\"first\">";
	private String botCode = "";
	boolean isActive = true;
	
	private final CaptchaDownloader captchaDownloader = new CaptchaDownloader();
   
	private RSEditorPane consoleRSPane;
	private RSEditorPane deleteRSPane;
   
	ArrayList<RedditPost> postArray = new ArrayList<RedditPost>();
	ArrayList<RedditPost> pastArray = new ArrayList<RedditPost>();			// because puns
	ArrayList<RedditPost> deletedArray = new ArrayList<RedditPost>();
   
	private boolean useAPIRequests = false;
	private boolean searchTopPosts = false;
	private boolean isSubredditAll = true;
	
	// Don't set the interval too low, you will make Reddit mad
	private int intervalPerPage = 3000;
	private int scourIntervalMinutes = 1;
	private int scourIntervalSeconds = 30;
   private int scourIntervalSecondsAll = 50;
	private int maxDeletesPerScour = 10;
	private int postsPerView = 25;
	private int currentRanking = 1;
	private int iterations = 4;
	private int lowEndScour = (iterations * postsPerView) - ((iterations * postsPerView)/20);
	private int apiLowEndScour = 22;
   private int apiLowEndScoutAll = 95;
	private int logCount = 0;
	
	private User user;
	private RestClient restClient;
	private Submissions submitter;
	private Comments commenter;
   
	public void run()
	{
		loadUserInfo();
		restClient = new HttpRestClient();
		restClient.setUserAgent(botCode);
		commenter = new Comments(restClient);
		user = new User(restClient,userName,password);
		submitter = new Submissions(restClient);
		try{
			user.connect();
		}catch(Exception e){
			consoleRSPane.append("Could not connect bot to reddit account");
		}
		while(isActive)
		{
			if(isSubredditAll || useAPIRequests == false)
			{
				scrapePages(redditRoot + redditLocation,postArray,true);
			}else{
				apiScrapePages(redditLocation,postArray,true);
			}
		}
		
		
	}

   //Methods
	
	// Compare two RedditPost arrays , the first being the current postings, latter previous,
	// and returns deleted posts in another array
	
	public ArrayList<RedditPost> comparePostArrays(ArrayList<RedditPost> currentPostings,
																	ArrayList<RedditPost> previousPostings)
	{
		ArrayList<RedditPost> deletedInCheck = new ArrayList<RedditPost>();
		for(int i = 0;i < previousPostings.size();i++)
		{
			RedditPost currentPost = previousPostings.get(i);
			if(currentPostings.contains(currentPost) == false)
			{
				// Check for ranking, if it is greater than the low end scour, it goes to a specific
				// subreddit to check for deletion
				if(isSubredditAll)
				{
					if(currentPost.getRanking() < lowEndScour && currentPost.getTitle().startsWith("s=\"share\"") == false)
					{
						deletedInCheck.add(currentPost);
					}else if(currentPost.getTitle().startsWith("s=\"share\"") == false && currentPost.getRanking() != 100){
						// Since posts naturally drop out of the top 100 around 95-100, check
						// any of the 90's for removal by checking the subreddit top
						// 100 posts. This slows the program down, mainly because Reddit
						// kindly asks to minimize requests to a certain amount in a certain
						// time.
						ArrayList<RedditPost> subredditCheck = new ArrayList<RedditPost>();
						//System.out.println("Scraping Subreddit : (" + redditRoot + currentPost.getSubreddit() + ") in > " + 
						//							lowEndScour + " scour");
						consoleRSPane.append("Scraping Subreddit : (" + redditRoot + currentPost.getSubreddit() + ") in > " + 
													lowEndScour + " scour");
						// Increase iteration to check the top 125, just to be sure
						/*
						iterations++;
						subredditCheck = scrapePages(redditRoot + currentPost.getSubreddit(), subredditCheck,false);
						iterations--;
						*/
						subredditCheck = apiScrapePages(currentPost.getSubreddit(), subredditCheck);
						//System.out.println("Current post is " + currentPost.getTitle() + "\n" + currentPost.getSubreddit() + 
                  //                   "\n" + currentPost.getLink());
						if(subredditCheck.contains(currentPost) == false)
						{
							//System.out.println("Adding deleted post : " + currentPost.getTitle() + "\n" + currentPost.getSubreddit() +
                  //                        "\n" + currentPost.getLink());
							deletedInCheck.add(currentPost);
						}else{
                  /*
                     for(int j = 0;j < subredditCheck.size();j++)
                     {
                        System.out.println("Scheck: " + subredditCheck.get(j).getTitle() + "\n" + subredditCheck.get(j).getSubreddit() + 
                                             "\n" + subredditCheck.get(j).getLink());
                     }
                  */
                  }
					}
				}else{
                  int lowEnd = 22;
                  if(currentPost.getSubreddit().equals("all") || currentPost.getSubreddit().equals("undelete")){
                     lowEnd = apiLowEndScoutAll;
                  }
						if(currentPost.getRanking() < lowEnd)
						{
							//System.out.println("Adding deleted post : " + currentPost.toString());
							deletedInCheck.add(currentPost);
						}/*else if(currentPost.getRanking() == apiLowEndScour){														This section is unreliable, needs work.
							ArrayList<RedditPost> subredditCheck = new ArrayList<RedditPost>();
							subredditCheck = scrapePages(redditRoot + currentPost.getSubreddit(),subredditCheck,false);
							if(subredditCheck.contains(currentPost) == false)
							{
								deletedInCheck.add(currentPost);
							}
						}*/
				}
			}
		}
		return deletedInCheck;
	}
   
	// Loads user info from userInfo.txt (it has to be in same working directory as the program
	public void loadUserInfo()
	{
		File userInfo = new File("userInfo.txt");
		if(userInfo.exists())
		{
			try{
				BufferedReader reader = new BufferedReader(new FileReader(userInfo));
				String line = reader.readLine();
				// Always trim in case people add spaces by accident
				userName = line.substring(line.indexOf("=") + 1).trim();
				line = reader.readLine();
				password = line.substring(line.indexOf("=") + 1).trim();
				line = reader.readLine();
				botCode = line.substring(line.indexOf("=") + 1).trim();
			}catch(Exception e)
			{
				consoleRSPane.append("Error loading user account info. Check .txt file "+
											"\n Format: " + "\nAccount Name = name\nPassword = password " +
											"\nBot Code = botcode");
			}
		}
	}
     
	// Populate the threads postArray. It's used for file writing and post submitting, along with the pastArray
	public void populateArray(String postings, ArrayList<RedditPost> arrayToFill)
	{
		// These expressions only hold relevence when starting from an html list item
		Pattern commentsRegex = Pattern.compile("\\d+ comments</a>");
		Pattern subredditRegex = Pattern.compile("r/\\w+/");
		Matcher matchRegex;
      
		while(postings.contains("\n"))
		{
			String currentLine = postings.substring(0,postings.indexOf("\n"));
			RedditPost currentPost = new RedditPost();
			currentPost.setRanking(currentRanking);
			
			currentPost.setTitle(currentLine.substring(0,currentLine.indexOf("</a>")));
			currentLine = currentLine.substring(currentLine.indexOf(listItem));
			
			postings = postings.substring(postings.indexOf("\n") + 1);
			currentLine = currentLine.substring(currentLine.indexOf("http"));
		  
			currentPost.setLink(currentLine.substring(currentLine.indexOf("http"),currentLine.indexOf("/\"")) + "/");
			matchRegex = commentsRegex.matcher(currentLine);
			if(matchRegex.find())
			{
				currentPost.setNumberOfComments(matchRegex.group(0).replace("</a>",""));   
			}
			
			matchRegex = subredditRegex.matcher(currentLine);
			if(matchRegex.find())
			{
				String titleTemp = matchRegex.group(0);
				titleTemp = titleTemp.substring(2);
				currentPost.setSubreddit(titleTemp.replace("/",""));
			}
			//System.out.println(currentPost.toString());
			arrayToFill.add(currentPost);
			currentRanking++;
		}
	}
	
	public void apiScrapePages(String locationIn,ArrayList<RedditPost> arrayToFill,boolean writeToFile)
	{
		boolean sentinel = false;
		
		while(sentinel == false)
		{
			try{
				List<Submission> postings = null;
				
				if(searchTopPosts == false){
					postings = submitter.getSubmissions(locationIn,
																	Submissions.Popularity.NEW,
																	Submissions.Page.FRONTPAGE,
																	user);
				}else{
					postings = submitter.getSubmissions(locationIn,
																	Submissions.Popularity.HOT,
																	Submissions.Page.FRONTPAGE,
																	user);
				}
				for(int i = 0;i < postings.size();i++)
				{
					RedditPost currentPost = new RedditPost();
					Submission clone = postings.get(i);
					
					currentPost.setTitle(clone.getTitle());
					currentPost.setRanking(i + 1);
					currentPost.setLink("http://www.reddit.com" + clone.getPermalink());
					currentPost.setNumberOfComments(Long.toString(clone.getCommentCount()) + " comments");
               currentPost.setLocked(clone.getLocked());
               if(redditLocation.equals("all")){
                 // System.out.println("Setting /all subreddit to: " + clone.getSubreddit());
                  currentPost.setSubreddit(redditLocation);
                  currentPost.setSubSubreddit(clone.getSubreddit());
               }else{
					   currentPost.setSubreddit(redditLocation);
               }
					//currentPost.setSubreddit(redditLocation);
					arrayToFill.add(currentPost);
					//System.out.println(currentPost.toString());
				}
				sentinel = true;
			}catch(Exception e){
				consoleRSPane.append("Unable to retrieve postings...trying again");
				try{
					TimeUnit.SECONDS.sleep(15);
				}catch(InterruptedException f){
				}
			}
		}
		if(arrayToFill.size() >= postsPerView - 1)
		{
			deleteCheck(writeToFile,arrayToFill);
		}else{
			consoleRSPane.append("Array didn't fill completely in: " + redditLocation);
		}
      int sleepTime = scourIntervalSeconds;
		if(locationIn.equals("all") || locationIn.equals("undelete")){
         sleepTime = scourIntervalSecondsAll;
      }
		//System.out.println("Sleeping " + scourIntervalSeconds + " seconds");
		consoleRSPane.append("Sleeping " + sleepTime + " seconds");

		try{
			TimeUnit.SECONDS.sleep(sleepTime);
		}catch(InterruptedException e){
		}
	}
   
   public ArrayList<RedditPost> apiScrapePages(String locationIn,ArrayList<RedditPost> arrayToFill)
	{
		boolean sentinel = false;
		
		while(sentinel == false)
		{
			try{
				List<Submission> postings = null;

				postings = submitter.getSubmissions(locationIn,
																Submissions.Popularity.HOT,
																Submissions.Page.FRONTPAGE,
																user);
                                                
				for(int i = 0;i < postings.size();i++)
				{
					RedditPost currentPost = new RedditPost();
					Submission clone = postings.get(i);
					
					currentPost.setTitle(clone.getTitle());
					currentPost.setRanking(i + 1);
					currentPost.setLink("http://www.reddit.com" + clone.getPermalink());
					currentPost.setNumberOfComments(Long.toString(clone.getCommentCount()) + " comments");
               currentPost.setLocked(clone.getLocked());
               if(locationIn.equals("all")){
                 // System.out.println("Setting /all subreddit to: " + clone.getSubreddit());
                  currentPost.setSubreddit(locationIn);
                  currentPost.setSubSubreddit(clone.getSubreddit());
               }else{
					   currentPost.setSubreddit(locationIn);
               }
					arrayToFill.add(currentPost);
					//System.out.println("API : http://www.reddit.com" + clone.getPermalink());
				}
				sentinel = true;
			}catch(Exception e){
				consoleRSPane.append("Unable to retrieve postings...trying again");
				try{
					TimeUnit.SECONDS.sleep(15);
				}catch(InterruptedException f){
				}
			}
		}
      try{
			TimeUnit.SECONDS.sleep(15);
		}catch(InterruptedException e){
		}
		return arrayToFill;
	}
	
	public void deleteCheck(boolean writeFiles,ArrayList<RedditPost> arrayToFill)
	{
		// Perform delete checks after pastArray has been filled
		
		//System.out.println("Comparing Data Sets for deletes...");
		ArrayList<RedditPost> deletesToAdd = comparePostArrays(postArray,pastArray);
		
		if(pastArray.size() != 0 && deletesToAdd.size() <= maxDeletesPerScour)
		{
			
			for(int i = 0;i < deletesToAdd.size();i++)
			{
				if(deletedArray.contains(deletesToAdd.get(i)) == false)
				{
					//System.out.println("Adding Deleted post: + " + deletesToAdd.get(i) + "\n");
					deletedArray.add(deletesToAdd.get(i));
				}
			}
			
			String fileName = "";
			if(isSubredditAll){
				fileName = redditLocation + " All ";
			}else if(searchTopPosts == false){
				fileName = redditLocation + " NEW ";
			}else if(searchTopPosts){
				fileName = redditLocation + " HOT ";
			}
			
			File deletes = new File(fileName + "(Deletes).html");
			if (!deletes.exists()) {
				try{
					deletes.createNewFile();//Always update file to keep a permanent reference
				}catch(IOException e){
				}
			}
			
			// Check over deletedArray for submissions. Posts are held on for several scrapes because sometimes posts
			// don't show up in a usual scrape, but will be there in a subsequent scrape. This causes the postings to
			// move slower than /r/undelete, which pulls it's postings through the reddit API, but this will allow us
			// to get posts that aren't filtered by Reddit. If a post is not found, it gets a check. After 3 checks
			// without being found, it's added to the queue for submission.
			for(int i = 0;i < deletedArray.size();i++)
			{
				if(deletedArray.get(i).isReadyToSubmit())
				{
					appendSingleDelete(deletes,deletedArray.get(i));
               deletedArray.get(i).setReadyToRemove();				
				}else{
					if(postArray.contains(deletedArray.get(i)) == false)
					{
						//System.out.println("Adding Check to " + deletedArray.get(i).toString() + " to submit\n");
						deletedArray.get(i).addCheck();
					}else{
						//System.out.println("Found post : " + deletedArray.get(i).toString() + " in double check \n");
						deletedArray.get(i).setReadyToRemove();
					}
				}
			}
			
			// Use an Iterator to iterate through posts to add and remove them simultaneously. You'd get index
			// out of bounds trying to use the normal array iterations in the rest of the program.  This loop
			// goes through the deletedArray again to remove all posts that are ready for submission from the
			// array and submit them.
			int i = deletedArray.size();
			for (Iterator<RedditPost> it = deletedArray.iterator(); it.hasNext(); )
			{
				RedditPost temp = it.next(); // Add this line in your code
				if (temp.isReadyToRemove())
				{
					//System.out.println("Removing : " + temp.toString() + " from deleted submissions");
					it.remove();
				}
				i++;
			}
		}
		if(writeFiles){
			String fileName = redditLocation;
			// Switch these two File lines to keep a constant log of the front page
			//File frontPage = new File(fileName + logCount + "(OLD).html");
			File frontPage = new File(fileName + "(OLD).html");
			//logCount++;
			if (!frontPage.exists()) {
				try{
					frontPage.createNewFile();
				}catch(IOException e){      
	         }
			}
			writeToFile(frontPage,arrayToFill);
		}
		pastArray = (ArrayList<RedditPost>)postArray.clone(); // clone. Cloning is important here.
		postArray.clear();    
		currentRanking = 1;
		
		//System.out.println("Deletes In Queue = " + deletedArray.size());
		consoleRSPane.append("Deletes In Queue = " + deletedArray.size());
	}
	
	// Repeatedly loads pages in a subreddit, reading html and making RedditPost objects
	public ArrayList<RedditPost> scrapePages(String locationIn,ArrayList<RedditPost> arrayToFill, boolean writeFiles)
	{
		// 4x25 = 100 posts. Iterations starts at 4 by default
		for(int i = 0;i < iterations;i++)
		{
			int retry = 0;
			// Attempt to connect 3 times. Bad things happen if there's a connection error.
			while(retry <= 3){
				try{
					String ip = "";
					String ipVisual = "";
					System.out.println("Scraping : " + locationIn);
					//consoleRSPane.append("Scraping : " + locationIn);
					URL redditURL = new URL(locationIn);
					//System.out.println("Test passed");
					
					URLConnection connection = redditURL.openConnection();
					connection.addRequestProperty("Protocol", "Http/1.1");
					connection.addRequestProperty("Connection", "keep-alive");
					connection.addRequestProperty("Keep-Alive", "1000");
					connection.addRequestProperty("User-Agent", "Web-Agent");
	            
					// It's better to not have so much code in a try block. But I still do.
					try{
						BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
						//System.out.println("Done opening input stream");
						if(in != null)
						{
							boolean sentinel = false;
							String lineAdd = in.readLine();            
							while(sentinel == false)
							{
								if(lineAdd != null)
								{
									ip += lineAdd;
									lineAdd = in.readLine();
								}else{
									sentinel = true;
									consoleRSPane.append("Finished Running HTML for : " + locationIn);
								}
							}
							// check for the next button with the contains() method. This is inefficient, but
							// the workload isn't high, so it's fine. It will be replaced with pattern matching to clean it up
							if(ip.contains(nextButton))
							{
								// The first page has no previous button
								if(i <= 0)
								{
									nextPage = ip.substring(ip.indexOf(nextButton));
									nextPage = nextPage.substring(nextPage.indexOf("http"));
									nextPage = nextPage.substring(0,nextPage.indexOf("\""));
									//consoleRSPane.append("NextPage " + i + " is " + nextPage);  
								}else{
									// skip the first link to go to next
									nextPage = ip.substring(ip.indexOf(nextButton));
									nextPage = nextPage.substring(nextPage.indexOf("http") + 1);
									nextPage = nextPage.substring(nextPage.indexOf("http"));
									nextPage = nextPage.substring(0,nextPage.indexOf("\""));
									//consoleRSPane.append("NextPage " + i + " is " + nextPage);  
								}
								locationIn = nextPage;
							}else{
								System.out.println("Page does not contain next button");
								System.out.println(ip);
							}
	                  
							sentinel = false;
	                  
							// Separate list items using liste item match
							while(sentinel == false)
							{
								try{
									String stringToAdd = ip.substring(ip.indexOf(titleFinder));
									String titleFullLength = ip.substring(ip.indexOf(tabIndexFinder) + tabIndexFinder.length());
									stringToAdd = ip.substring(ip.indexOf(listItem));
									ip = ip.substring(ip.indexOf(listItem));
									stringToAdd = titleFullLength + stringToAdd.substring(0,stringToAdd.indexOf("</a>") + 4);
									ip = ip.substring(ip.indexOf("</a>") + 4);
									ipVisual += stringToAdd + "\n";
								}catch(Exception e){
									sentinel = true;
								}
							}
							// take the string of list items and send it for population
							populateArray(ipVisual,arrayToFill);
							retry = 4;
							in.close();
							try{
								// Prevent over requesting Reddit
								Thread.sleep(intervalPerPage);
							}catch(InterruptedException e){
							}
						}else{
							System.out.println("The input reader is null");
						}
					}catch(IOException f){
						System.out.println("Error establishing connection stream");
						consoleRSPane.append("Error establishing connection stream to " + locationIn);
						retry++;
						try{
							TimeUnit.SECONDS.sleep(15);
						}catch(InterruptedException e){
						}
						if(retry >= 3){
							postArray.clear();
							pastArray.clear();
							retry = 4;
						}
					}
				}catch(IOException e){
					System.out.println("Error establishing overall connection");
					consoleRSPane.append("Error establishing overall connection");
					retry++;
					try{
							TimeUnit.SECONDS.sleep(15);
					}catch(InterruptedException f){
					}
					if(retry >= 3){
						postArray.clear();
						pastArray.clear();
						retry = 4;
					}
				}
			}
		}
		
		if(writeFiles == false)
		{
			return arrayToFill;
		}else{
			deleteCheck(writeFiles,arrayToFill);
		}
		//System.out.println("Sleeping " + scourIntervalMinutes + " minutes");
		consoleRSPane.append("Sleeping " + scourIntervalMinutes + " minutes");
		try{
			TimeUnit.MINUTES.sleep(scourIntervalMinutes);
		}catch(InterruptedException e){
		}
		return arrayToFill;
	}
	
	// Write an array to given file
	public synchronized void writeToFile(File fileIn,ArrayList<RedditPost> arrayIn)
	{
		//System.out.println("Writing file: " + fileIn.getAbsolutePath());
		BufferedWriter out = null;
		try{
			FileWriter fw = new FileWriter(fileIn.getAbsoluteFile());
			out = new BufferedWriter(fw);
			for(int i = 0;i < arrayIn.size();i++)
			{
				out.write(arrayIn.get(i).toString());
				out.write("\n\t" + arrayIn.get(i).getLink() + "\n");
			}
			out.close();
		}catch(IOException e){
      }
	}
	
	// append given post to given file
	public synchronized void appendSingleDelete(File fileIn, RedditPost postIn)
	{
		//System.out.println("Appending delete File: " + fileIn.getAbsolutePath());
		BufferedWriter out = null;
		Boolean postIt = false;
		
		try{
			FileWriter fw = new FileWriter(fileIn.getAbsoluteFile(),true);
			out = new BufferedWriter(fw);
         ArrayList<RedditPost> subredditCheck = new ArrayList<RedditPost>();
			if(postIn.isReadyToSubmit())
			{
            subredditCheck = apiScrapePages(postIn.getSubreddit(), subredditCheck);
				//System.out.println("Current subreddit is " + postIn.getSubreddit());
            for(int j = 0;j < subredditCheck.size();j++)
            {
               //System.out.println("Scheck: " + subredditCheck.get(j).getTitle() + "\n" + subredditCheck.get(j).getSubreddit() + 
               //                     "\n" + subredditCheck.get(j).getLink());
            }
				if(subredditCheck.contains(postIn) == false && postIn.getRanking() < 98)
				{
					//System.out.println("Adding deleted post : " + postIn.toString());
               out.append(postIn.toString());
   				deleteRSPane.append(postIn.toString());
   				// add html anchors to make clickable links in logs and delete pane
   				out.append("\n\t" + "<a href=\"" + postIn.getLink() + "\">" + postIn.getLink() + "</a>" + "\n");
   				deleteRSPane.append("<a href=\"" + postIn.getLink() + "\">" + postIn.getLink() + "</a>" + "\n");
   				postIn.setReadyToRemove();
					postIt = true;
				}else{
               postIn.setReadyToRemove();
            }
			}else{
				if(postArray.contains(postIn) == false)
				{
					postIn.setReadyToSubmit();
				}else{
					postIn.setReadyToRemove();
				}
			}
			out.close();
		}catch(IOException e){
		}
		// Post it online. Super simple.
		RestClient restClient = new HttpRestClient();
		// Bot code here, make sure to add your own unique version number in userInfo.txt
		restClient.setUserAgent(botCode);
		User user = new User(restClient,userName, password);
		if(postIt == true)
			{
			try{
				user.connect();
            //if(postIn.getSubreddit().equals("all")){
               //user.submitLink(postIn.toString(),postIn.getLink(),postIn.getSubreddit());
            //}else{
				   user.submitLink(postIn.toString(),postIn.getLink(),submitSubreddit);
            //}
				// If you require captcha fill-in, this will generate a .png of the appropriate captcha. This feature will
				// be useful if your bot isn't posting and you don't know why. Karma, baby. It's the karma.
				
				Captcha cappyCap = new Captcha(restClient,captchaDownloader);
				if(cappyCap.needsCaptcha(user))
				{
					//System.out.println("Requires Captcha");
					consoleRSPane.append("Requires Captcha!");
					String iden = cappyCap.newCaptcha(user);
					captchaDownloader.getCaptchaImage(iden);
				}else{
					//System.out.println("Bot Post Sucess");
					consoleRSPane.append("Bot Post Success");
				}
				// This is the start of the code for getting the comments from the deleted post and posting the top on the undelete post.
				// currently it tries to post on the deleted post, which you can't do.
				/*
				try{
					String commentsSubstringer = "/comments/";
					String link = postIn.getLink();
					List<Comment> commentsFromDelete = commenter.commentsFromArticle(redditLocation,
																										link.substring(link.indexOf(commentsSubstringer) + commentsSubstringer.length()),
																										"",
																										0,
																										0,
																										0,
																										CommentSort.TOP);
					Submission linkToComment = new Submission();
					linkToComment.setUrl(postIn.getLink());
					linkToComment.setTitle(postIn.getTitle());
					linkToComment.setUser(user);
					if(commentsFromDelete.size() != 0)
					{
						linkToComment.comment(commentsFromDelete.get(0).getBody());
					}
				}catch(Exception e)
				{
					consoleRSPane.append("Error posting links");
				}
				*/
			}catch(Exception e){
				System.out.println("Could not connect bot");
			}
		}    
	}
   
	// Mutations
	
	public void setRedditLocation(String locationIn)
	{
		redditLocation = locationIn;
	}
	
	public String getRedditLocation()
	{
		return redditLocation;
	}
	
	public void setConsoleRSPane(RSEditorPane paneIn)
	{
		consoleRSPane = paneIn;
	}
	
	public RSEditorPane getConsoleRSPane()
	{
		return consoleRSPane;
	}
	
	public void setDeleteRSPane(RSEditorPane paneIn)
	{
		deleteRSPane = paneIn;
	}
	
	public RSEditorPane getDeleteRSPane()
	{
		return deleteRSPane;
	}
	
	public ArrayList<RedditPost> getPostArray()
	{
		return postArray;
	}
	
	public void setPostArray(ArrayList<RedditPost> arrayIn)
	{
		postArray = arrayIn;
	}
	
	public ArrayList<RedditPost> getPastArray()
	{
		return pastArray;
	}
	
	public void setPastArray(ArrayList<RedditPost> arrayIn)
	{
		pastArray = arrayIn;
	}
	
	public ArrayList<RedditPost> getDeletedArray()
	{
		return deletedArray;
	}
	
	public void setDeletedArray(ArrayList<RedditPost> arrayIn)
	{
		deletedArray = arrayIn;
	}
	
	public void setSearchTopPosts(boolean decision)
	{
		searchTopPosts = decision;
	}
	
	public void setSubmitSubreddit(String subredditIn)
	{
		submitSubreddit = subredditIn;
	}
	
	public void setUseAPIRequests(boolean decision)
	{
		useAPIRequests = decision;
	}
	
	public void setIsSubredditAll(boolean decision)
	{
		isSubredditAll = decision;
	}
	
	public boolean isWatching()
	{
		return isActive;
	}
	
	public void setWatching(boolean toWatch)
	{
		isActive = toWatch;
	}
	// Objects
   
	// A redditPost contains all the general information you'll likely need about a post
	public class RedditPost
	{
		// a list of special characters to replace. This list is growing, bear with me.    
		private String quotes = "&quot;";
		private String funkyApostrophe = "’";
		private String downArrow = "↓";
		private String downRightArrow =  "↘";
		private String rightArrow = "→";
      private String accentE = "é";
      private String differentHyphen = "–";
		private String eyeWithADotOnTop = "İ";
		private String stupidApostrophe = "‘";
		private String funkyQuote = "�";
		
		
		private String title;
		private String linkLocation;
		private String subreddit;
      private String subsubreddit;
		private String numberOfComments;
		private int ranking;
		private boolean readyToSubmit;
		private boolean readyToRemove;
      private boolean locked;
		
		int checks;
		
		public RedditPost()
		{
			title = "";
			linkLocation = "";
			subreddit = "";
         subsubreddit = "";
			numberOfComments = "0";
			ranking = 0;
			readyToSubmit = false;
			readyToRemove = false;
			checks = 0;
		}
		
		public void addCheck()
		{
			if(checks >= 1)
			{
				readyToSubmit = true;
			}else{
				checks++;
			}
		}
		public String getTitle()
		{
			return title;
		}
		
		public void setTitle(String titleIn)
		{
			// Remove those funky characters.
         
			titleIn = titleIn.replace(quotes,"\"");
			titleIn = titleIn.replace(downArrow,"\u2193");
			titleIn = titleIn.replace(downRightArrow,"\u2198");
			titleIn = titleIn.replace(rightArrow,"\u2192");
			titleIn = titleIn.replace(funkyApostrophe,"\'");
			titleIn = titleIn.replace(differentHyphen,"-");
			titleIn = titleIn.replace(eyeWithADotOnTop,"\u0049");
			titleIn = titleIn.replace(stupidApostrophe,"�");
         titleIn = titleIn.replace(funkyQuote,"\"");
         //titleIn = titleIn.replace(accentE,"\\u201");
			
			if(titleIn.length() >= 250)
			{
				titleIn = titleIn.substring(0,250);
			}
         String results = StringEscapeUtils.unescapeHtml4(titleIn);
         results = StringEscapeUtils.unescapeXml(results);
         //results = escapeNonAscii(results);
			title = results;
		}
		
		public String getLink()
		{
			return linkLocation;
		}
      
		public void setLink(String linkIn)
		{
			linkLocation = linkIn;
		}
      
		public String getSubreddit()
		{
			return subreddit;
		}
      
		public void setSubreddit(String subredditIn)
		{
			subreddit = subredditIn;
		}
      
      public String getSubSubreddit()
		{
			return subsubreddit;
		}
      
		public void setSubSubreddit(String subredditIn)
		{
			subsubreddit = subredditIn;
		}
      
		public String getNumberOfComments()
		{
			return numberOfComments;
		}
      
		public void setNumberOfComments(String numberOfCommentsIn)
		{
			numberOfComments = numberOfCommentsIn;
		}
      
		public int getRanking()
		{
			return ranking;
		}
      
		public void setRanking(int rankingIn)
		{
			ranking = rankingIn;
		}
      
      public boolean getLocked(){
         return locked;
      }
      
      public void setLocked(boolean lockedIn){
         locked = lockedIn;
      }
      
		public String toString()
		{
         String lockedString = "";
         if(locked){
            lockedString = "Locked:";
         }
         
         if(subsubreddit.equals("")){
            String outString = lockedString + "[#" + ranking + "]" + "\t" + title + "\t" + "[" + subreddit + "]\t" + numberOfComments;
   			if(outString.length() >= 290)
   			{
   				outString = outString.substring(0,290);
   			}
   			return outString;
         }else{
            String outString = lockedString + "[#" + ranking + "]" + "\t" + title + "\t" + "[" + subsubreddit + "]\t" + numberOfComments;
   			if(outString.length() >= 290)
   			{
   				outString = outString.substring(0,290);
   			}
            return outString;
         }
		}
		
		public void setReadyToSubmit()
		{
			readyToSubmit = true;
		}
		
		public boolean isReadyToSubmit()
		{
			return readyToSubmit;
		}
		
		public void setReadyToRemove()
		{
			readyToRemove = true;
		}
		
		public boolean isReadyToRemove()
		{
			return readyToRemove;
		}
		
		public boolean equals(Object postIn)
		{
			if(postIn == this)
			{
			 return true;
			}
			if(postIn == null || postIn.getClass() != this.getClass())
			{
				return false;
			}
			
			RedditPost otherPost = (RedditPost)postIn;
			if(otherPost.getLink().equals(linkLocation) && otherPost.getTitle().equals(title) && 
				otherPost.getSubreddit().equals(subreddit) && otherPost.getLocked() == locked)
			{
				return true;
			}
			return false;
		} 
   }
   private static String escapeNonAscii(String str) {

     StringBuilder retStr = new StringBuilder();
     for(int i=0; i<str.length(); i++) {
       int cp = Character.codePointAt(str, i);
       int charCount = Character.charCount(cp);
       if (charCount > 1) {
         i += charCount - 1; // 2.
         if (i >= str.length()) {
           throw new IllegalArgumentException("truncated unexpectedly");
         }
       }
   
       if (cp < 128) {
         retStr.appendCodePoint(cp);
       } else {
         retStr.append(String.format("\\u%x", cp));
       }
     }
     return retStr.toString();
   }
}