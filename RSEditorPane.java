/*
   Override and add a few methods for ease of use
*/

import javax.swing.*;

public class RSEditorPane extends JTextPane
	{
		private String body = "";
		private String htmlStart = "<html>";
		private String htmlEnd = "</html>";
		private String pStart = "<p>";
		private String pEnd = "</p>";
		
		public RSEditorPane()
		{
			super();
		}
		
		public String getBody()
		{
			return body;
		}
		
		public void setBody(String bodyIn)
		{
			body = bodyIn;
			updatePane();
		}
		
		public synchronized void append(String sIn)
		{
			body = pStart + sIn + pEnd + body;
			updatePane();
		}
		
		public void updatePane()
		{
			setText(htmlStart + pStart + body + pEnd + htmlEnd);
		}
		
	}