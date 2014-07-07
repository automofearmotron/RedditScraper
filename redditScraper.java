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
	Lot's of thanks to a guy named Karan who had a nifty Java API wrapper called JReddit on Github. Saved me a lot of time.
*/



import javax.swing.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;
import java.util.concurrent.TimeUnit;

import com.github.jreddit.user.User;
import com.github.jreddit.captcha.*;
import com.github.jreddit.utils.restclient.HttpRestClient;
import com.github.jreddit.utils.restclient.RestClient;

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

public class redditScraper implements Runnable
{
	private String redditOriginalLocation = "http://www.reddit.com/r/all";
   private String nextButton = "<span class=\"nextprev\">";
   private String nextPage = "";
   private String redditLocation = "http://www.reddit.com/r/all";
	private String redditRoot = "http://www.reddit.com/";
   private String userName = "";
   private String password = "";
	private String submitSubreddit = "";
   private String listItem = "<li class=\"first\">";
   private String botCode = "blank";
	boolean isActive = true;
	
   private final CaptchaDownloader captchaDownloader = new CaptchaDownloader();
   
	private RSEditorPane consoleRSPane;
	private RSEditorPane deleteRSPane;
   
   ArrayList<RedditPost> postArray = new ArrayList<RedditPost>();
	ArrayList<RedditPost> pastArray = new ArrayList<RedditPost>();			// because puns
	ArrayList<RedditPost> deletedArray = new ArrayList<RedditPost>();
   
	// Don't set the interval too low, you will make Reddit mad
	private int intervalPerPage = 3000;
   private int scourIntervalMinutes = 2;
	private int postsPerView = 25;
   private int currentRanking = 1;
   private int iterations = 4;
   private int lowEndScour = 92;
   
	public void run()
	{
		loadUserInfo();
		while(isActive)
		{
			scrapePages(redditLocation,postArray);
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
				if(currentPost.getRanking() < lowEndScour)
				{
               System.out.println("Adding deleted post : " + currentPost.toString());
					deletedInCheck.add(currentPost);
				}else{
					// Since posts naturally drop out of the top 100 around 95-100, check
					// any of the 90's for removal by checking the subreddit top
					// 100 posts. This slows the program down, mainly because Reddit
					// kindly asks to minimize requests to a certain amount in a certain
					// time.
					ArrayList<RedditPost> subredditCheck = new ArrayList<RedditPost>();
               System.out.println("Scraping Subreddit : (" + redditRoot + currentPost.getSubreddit() + ") in > " + 
                                    lowEndScour + " scour");
					consoleRSPane.append("Scraping Subreddit : (" + redditRoot + currentPost.getSubreddit() + ") in > " + 
                                    lowEndScour + " scour");
               // Increase iteration to check the top 125, just to be sure
               iterations++;
					subredditCheck = scrapeSubreddit(redditRoot + currentPost.getSubreddit(), subredditCheck);
               iterations--;
					if(subredditCheck.contains(currentPost) == false)
					{
                  System.out.println("Adding deleted post : " + currentPost.toString());
						deletedInCheck.add(currentPost);
					}else{
						
					}
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
         userName = line.substring(line.indexOf("=") + 1).trim();
         line = reader.readLine();
         password = line.substring(line.indexOf("=") + 1).trim();
         line = reader.readLine();
         submitSubreddit = line.substring(line.indexOf("=") + 1).trim();
         line = reader.readLine();
         botCode = line.substring(line.indexOf("=") + 1).trim();
         }catch(Exception e)
         {
            consoleRSPane.append("Error loading user account info. Check .txt file "+
                                 "\n Format: " + "\nAccount Name = name\nPassword = password " +
                                   "\nSubreddit to Post = subreddit\nBot Code = botcode");
         }
      }
   }
   
   
   // Populate the threads postArray. It's used for file writing and post submitting, along with the pastArray
   public void populatePostArray(String postings, ArrayList<RedditPost> arrayToFill)
   {
      // These expressions only hold relevence when starting from an html list item
      Pattern titleRegex = Pattern.compile("\\w+/\"");
      Pattern commentsRegex = Pattern.compile("\\d+ comments</a>");
      Pattern subredditRegex = Pattern.compile("r/\\w+/");
      Matcher matchRegex;
      
      while(postings.contains("\n"))
      {
        String currentLine = postings.substring(0,postings.indexOf("\n"));
        RedditPost currentPost = new RedditPost();
        currentPost.setRanking(currentRanking);
         
        matchRegex = titleRegex.matcher(currentLine);
        if (matchRegex.find())
        {
            currentPost.setTitle(matchRegex.group(0).replace("/\"","").replace("_"," "));
         }
        postings = postings.substring(postings.indexOf("\n") + 1);
        currentPost.setLink(currentLine.substring(currentLine.indexOf("http"),currentLine.indexOf("/\"")));
        matchRegex = commentsRegex.matcher(currentLine);
        if(matchRegex.find())
        {
            currentPost.setNumberOfComments(matchRegex.group(0).replace("</a>",""));   
        }
        
        matchRegex = subredditRegex.matcher(currentLine);
        if(matchRegex.find())
        {
            currentPost.setSubreddit(matchRegex.group(0));
        }
        
        arrayToFill.add(currentPost);
        currentRanking++;
      }
   }
   
   // Repeatedly loads pages in a subreddit, reading html and making RedditPost objects
   public void scrapePages(String locationIn,ArrayList<RedditPost> arrayToFill)
   {
      // 4x25 = 100 posts. Iterations starts at 4 by default
      for(int i = 0;i < iterations;i++)
      {
         try{
            String ip = "";
            String ipVisual = "";
            System.out.println("Scraping : " + locationIn);
				consoleRSPane.append("Scraping : " + locationIn);
            URL redditURL = new URL(locationIn);
   			System.out.println("Test passed");
            
   		   URLConnection connection = redditURL.openConnection();
   		   connection.addRequestProperty("Protocol", "Http/1.1");
   		   connection.addRequestProperty("Connection", "keep-alive");
   		   connection.addRequestProperty("Keep-Alive", "1000");
   		   connection.addRequestProperty("User-Agent", "Web-Agent");
            
            // It's better to not have so much code in a try block. But I still do.
   			try{
   				BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
   				System.out.println("Done opening input stream");
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
                        System.out.println("Finished Running HTML for : " + locationIn);
								consoleRSPane.append("Finished Running HTML for : " + locationIn);
                     }
                     
                  }
                  // check for the next button with the contains() method. This is inefficient, but
                  // the workload isn't high, so it's fine.
                  if(ip.contains(nextButton))
                  {
                     // The first page has no previous button
                     if(i <= 0)
                     {
                        nextPage = ip.substring(ip.indexOf(nextButton));
                        nextPage = nextPage.substring(nextPage.indexOf("http"));
                        nextPage = nextPage.substring(0,nextPage.indexOf("\""));
                        System.out.println("NextPage " + i + " is " + nextPage);
								consoleRSPane.append("NextPage " + i + " is " + nextPage);  
                     }else{
                        // skip the first link to go to next
                        nextPage = ip.substring(ip.indexOf(nextButton));
                        nextPage = nextPage.substring(nextPage.indexOf("http") + 1);
								//
                        nextPage = nextPage.substring(nextPage.indexOf("http"));
                        nextPage = nextPage.substring(0,nextPage.indexOf("\""));
                        System.out.println("NextPage " + i + " is " + nextPage);
								consoleRSPane.append("NextPage " + i + " is " + nextPage);  
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
                        String stringToAdd = ip.substring(ip.indexOf(listItem));
                        ip = ip.substring(ip.indexOf(listItem));
                        stringToAdd = stringToAdd.substring(0,stringToAdd.indexOf("</a>") + 4);
                        ip = ip.substring(ip.indexOf("</a>") + 4);
                        ipVisual += stringToAdd + "\n";
                     }catch(Exception e)
                     {
                        sentinel = true;
                     }
                  }
						try{
                     // Prevent over requesting Reddit
							Thread.sleep(intervalPerPage);
						}catch(InterruptedException e)
						{
						
						}
                  // take the string of list items and send it for population
                  populatePostArray(ipVisual,arrayToFill);
                  
                  in.close();
   				}else{
   					System.out.println("The input reader is null");
   				}
   			}catch(IOException f)
   			{
   				System.out.println("Error establishing connection stream");
					consoleRSPane.append("Error establishing connection stream to " + locationIn);  
   			}
   		}catch(IOException e)
   		{
   			System.out.println("Error establishing overall connection");
				consoleRSPane.append("Error establishing overall connection"); 
   		}
      }
      
      // Perform delete checks after pastArray has been filled
		if(pastArray.size() != 0)
		{
         System.out.println("Comparing Data Sets for deletes...");
			ArrayList<RedditPost> deletesToAdd = comparePostArrays(postArray,pastArray);
			for(int i = 0;i < deletesToAdd.size();i++)
			{
            System.out.println("Deleted Test on: " + deletesToAdd.get(i).toString());
				if(deletedArray.contains(deletesToAdd.get(i)) == false)
				{
					System.out.println("Adding Deleted post: + " + deletesToAdd.get(i) + "\n");
					deletedArray.add(deletesToAdd.get(i));
				}
			}
			String fileName = locationIn.substring(locationIn.indexOf("r/") + 2);
                  
         fileName = fileName.substring(0,fileName.indexOf("/"));
         File deletes = new File(fileName + "(Deletes).html");
         if (!deletes.exists()) {
	         try{
				   deletes.createNewFile();//Always update file to keep a permanent reference
	         }catch(IOException e)
	         {
	         
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
						System.out.println("Adding Check to " + deletedArray.get(i).toString() + " to submit\n");
						deletedArray.get(i).addCheck();
					}else{
						System.out.println("Found post : " + deletedArray.get(i).toString() + " in double check \n");
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
				  		System.out.println("Removing : " + temp.toString() + " from deleted submissions");
		            it.remove();
		        }
		        i++;
		    }
		}
      
		String fileName = locationIn.substring(locationIn.indexOf("r/") + 2);
      fileName = fileName.substring(0,fileName.indexOf("/"));
      File frontPage = new File(fileName + "(OLD).html");
      
		if (!frontPage.exists()) {
         try{
			   frontPage.createNewFile();
         }catch(IOException e)
         {
         
         }
		}
      
      writeToFile(frontPage,arrayToFill);
  		pastArray = (ArrayList<RedditPost>)postArray.clone(); // clone. Cloning is important here.
		postArray.clear();    
		currentRanking = 1;
		
      System.out.println("Deletes In Queue = " + deletedArray.size());
      consoleRSPane.append("Deletes In Queue = " + deletedArray.size());
		System.out.println("Sleeping " + scourIntervalMinutes + " minutes");
		consoleRSPane.append("Sleeping " + scourIntervalMinutes + " minutes");
		try{
			TimeUnit.MINUTES.sleep(scourIntervalMinutes);
		}catch(InterruptedException e)
		{
		
		}
   }
   
   // same as the other scrape method, except no file writing and it checks a specific subreddit
   public ArrayList<RedditPost> scrapeSubreddit(String locationIn,ArrayList<RedditPost> arrayToFill)
   {
      for(int i = 0;i < iterations;i++)
      {
         try{
            String ip = "";
            String ipVisual = "";
            System.out.println("Scraping Subreddit: " + locationIn);
				consoleRSPane.append("Scraping Subreddit: " + locationIn);
            URL redditURL = new URL(locationIn);
   			System.out.println("Test passed");
            
   		   URLConnection connection = redditURL.openConnection();
   		   connection.addRequestProperty("Protocol", "Http/1.1");
   		   connection.addRequestProperty("Connection", "keep-alive");
   		   connection.addRequestProperty("Keep-Alive", "1000");
   		   connection.addRequestProperty("User-Agent", "Web-Agent");
   
   			try{
   				BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
   				System.out.println("Done opening input stream");
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
                        System.out.println("Finsihed Running HTML for : " + locationIn);
								consoleRSPane.append("Finsihed Running HTML for : " + locationIn);
                     }
                     
                  }
                  
                  if(ip.contains(nextButton))
                  {
                     if(i <= 0)
                     {
                        nextPage = ip.substring(ip.indexOf(nextButton));
                        nextPage = nextPage.substring(nextPage.indexOf("http"));
                        nextPage = nextPage.substring(0,nextPage.indexOf("\""));
                     }else{
                        nextPage = ip.substring(ip.indexOf(nextButton));
                        nextPage = nextPage.substring(nextPage.indexOf("http") + 1);
                        // Double the iteration to get to the next button instead of previous
                        nextPage = nextPage.substring(nextPage.indexOf("http"));; 
                        nextPage = nextPage.substring(0,nextPage.indexOf("\""));
                        System.out.println("NextSubPage " + i + " is " + nextPage);
								consoleRSPane.append("NextSubPage " + i + " is " + nextPage);               
							}
                  }else{
                     System.out.println("Subreddit does not contain next button");
                     System.out.println(ip);
							nextPage = locationIn;
                  }
                  System.out.println("Settings Subreddit Next Page to : " + nextPage);
                  locationIn = nextPage;
                  sentinel = false;
                  
                  while(sentinel == false)
                  {
                     try{                        
                        String stringToAdd = ip.substring(ip.indexOf(listItem));
                        ip = ip.substring(ip.indexOf(listItem));
                        stringToAdd = stringToAdd.substring(0,stringToAdd.indexOf("</a>") + 4);
                        ip = ip.substring(ip.indexOf("</a>") + 4);
                        ipVisual += stringToAdd + "\n";
                     }catch(Exception e)
                     {
                        sentinel = true;
                     }
                     
                  }
                  
						try{
							Thread.sleep(intervalPerPage);
						}catch(InterruptedException e)
						{
						
						}
                  populatePostArray(ipVisual,arrayToFill);
                  
                  in.close();
   				}else{
   					System.out.println("The input reader is null");
   				}
   			}catch(IOException f)
   			{
   				System.out.println("Error establishing connection stream");
					consoleRSPane.append("Error establishing connection stream");
   			}
   		}catch(IOException e)
   		{
   			System.out.println("Error establishing overall connection");
   		}
      }
      return arrayToFill;
		
   }
	
   // Write an array to given file
	public synchronized void writeToFile(File fileIn,ArrayList<RedditPost> arrayIn)
	{
      System.out.println("Writing file: " + fileIn.getAbsolutePath());
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
      }catch(IOException e)
      {
      
      }
	}
	
   // append given post to given file
	public synchronized void appendSingleDelete(File fileIn, RedditPost postIn)
	{
		System.out.println("Appending delete File: " + fileIn.getAbsolutePath());
		BufferedWriter out = null;
      
      try{
   		FileWriter fw = new FileWriter(fileIn.getAbsoluteFile(),true);
   		out = new BufferedWriter(fw);
			if(postIn.isReadyToSubmit())
			{
      		out.append(postIn.toString());
				deleteRSPane.append(postIn.toString());
      		out.append("\n\t" + "<a href=\"" + postIn.getLink() + "\">" + postIn.getLink() + "</a>" + "\n");
				//deleteRSPane.append(postIn.getLink());
            deleteRSPane.append("<a href=\"" + postIn.getLink() + "\">" + postIn.getLink() + "</a>" + "\n");
			}else{
				if(postArray.contains(postIn) == false)
				{
					postIn.setReadyToSubmit();
				}else{
					postIn.setReadyToRemove();
				}
			}
         out.close();
      }catch(IOException e)
      {
      
      }
      // Post it online. Super simple.
      RestClient restClient = new HttpRestClient();
      restClient.setUserAgent(botCode);
      User user = new User(restClient,userName, password); 
      try{
         user.connect();
         user.submitLink(
                postIn.getRanking() + " | " + postIn.getTitle() + " " + postIn.getSubreddit() + " " + postIn.getNumberOfComments(),
                postIn.getLink(),
                submitSubreddit);
         // If you require captcha fill-in, this will generate a .png of the appropriate captcha. This feature will
         // be useful if your bot isn't posting and you don't know why. Karma, baby. It's the karma.
         
         Captcha cappyCap = new Captcha(restClient,captchaDownloader);
         if(cappyCap.needsCaptcha(user))
         {
            System.out.println("Requires Captcha");
            consoleRSPane.append("Requires Captcha!");
            String iden = cappyCap.newCaptcha(user);
            captchaDownloader.getCaptchaImage(iden);
            if(cappyCap.needsCaptcha(user) == false)
            {
               System.out.println("Post success!");
               consoleRSPane.append("Post success!");
            }
            
         }else{
            System.out.println("Does not require Captcha first go around");
            consoleRSPane.append("Does not require Captcha first go around");
         }
         
      }catch(Exception e)
      {
         System.out.println("Could not connect bot");
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
      private String title;
      private String linkLocation;
      private String subreddit;
      private String numberOfComments;
      private int ranking;
      private boolean readyToSubmit;
		private boolean readyToRemove;
		
		int checks;
      
      public RedditPost()
      {
         title = "";
         linkLocation = "";
         subreddit = "";
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
         title = titleIn;
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
      
      public String toString()
      {
         return ranking + "\t|\t" + title + "\t(" + subreddit + ") \t" + numberOfComments;
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
				otherPost.getSubreddit().equals(subreddit))
			{
				return true;
			}
			return false;
		} 
   }
}