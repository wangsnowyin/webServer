/**
 * Created by Xueyin Wang and Xiaoyang Xu
 * on May 2
 */

import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.io.*;

public class HTTPServer {
	
	private static final String FILEPATH = System.getProperty("user.dir") + "/www";
	
	private static Hashtable<String, String> redirectMap; //to save redirect map
	
	// map for mime types
	private static final Map<String, String> mimeTypes = new HashMap<String, String>() {{
		put("html", "text/html");
		put("txt", "text/plain");
		put("pdf", "application/pdf");
		put("png", "image/png");
		put("jpeg", "image/jpeg");
		put("", "\r\n");
	}};
	
	private static class RequestHandler implements Runnable {
		
		private Socket socket;
		private BufferedReader fromClient;
		private String inline;
		private BufferedOutputStream toClient;
		
		public RequestHandler(Socket connection){
			this.socket = connection;
		}
		
		@Override
		public void run() {
			try {				
				String statusCode = "";
				String fileName = "";
				String redirectURL = "";
				boolean isHead = false;
				boolean isFile = false;
				boolean isRedirct = false;
				StringBuffer response = new StringBuffer();
				
				//initiate input and output stream of socket
				this.fromClient = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				this.toClient = new BufferedOutputStream(socket.getOutputStream());
				
				//get and tokenize the HTTP request from client
				inline = fromClient.readLine();
				String header = inline;
				StringTokenizer tokenizer = new StringTokenizer(header);
				String HTTPMethod = tokenizer.nextToken();
				String HTTPQuery = tokenizer.nextToken();
				System.out.println(HTTPQuery);
				
				if(HTTPMethod.equals("GET") || HTTPMethod.equals("HEAD")){ //the GET method
					if(HTTPMethod.equals("HEAD")) isHead = true;
					
					if(HTTPQuery.equals("/")){ //root direct
						statusCode = "200 OK";
						isFile = true;
						fileName = "index.html";

					}else if(HTTPQuery.equals("/redirect.defs")){ //fetch redirect.defs
						statusCode = "404 Not Found";

					}else if(redirectMap.containsKey(HTTPQuery)){ //redirect case
						statusCode = "301 Moved Permanently";
						redirectURL = redirectMap.get(HTTPQuery);
						System.out.println("redirectURL: " + redirectURL);
						isRedirct = true;

					}else{
						fileName = HTTPQuery.substring(HTTPQuery.indexOf("/"));
						File file = new File(FILEPATH + fileName);
						if(file.exists() && file.isFile()){ //if find the file
							statusCode = "200 OK";
							isFile = true;
						}else{ //file not found
							statusCode = "404 Not Found";
						}
					}
				}else{ //not support request method, 403
					statusCode = "403 Forbidden";
				}
				
				if(isFile){
					responseData(statusCode, fileName, toClient, isFile, isHead, isRedirct, redirectURL);
				}else{
					//to store other info of request except header
					response.append(inline);
					while(fromClient.ready()){
						inline = fromClient.readLine();
						response.append(inline);
					}
					
					responseData(statusCode, response.toString(), toClient, isFile, isHead, isRedirct, redirectURL);
				}
				
				toClient.flush();
				toClient.close();
				fromClient.close();
				
			} catch(Exception e) {
				System.err.println(e.getMessage());
			}
		}
		
	}
	
	private static void responseHeader(String status_code, String mimeType, int content_length, BufferedOutputStream toClient, boolean isRedirct, String redirectURL) throws Exception {
		String server = "Server: Java HTTPServer";
		toClient.write(("HTTP/1.1 " + status_code + "\r\n").getBytes());	
		if(isRedirct){
			toClient.write(("Location: " + redirectURL + "\r\n").getBytes());
		}
		toClient.write((server + "\r\n").getBytes());
		toClient.write(("Content-Type: " + mimeTypes.get(mimeType) + "\r\n").getBytes());
		toClient.write(("Content-Length: " + content_length + "\r\n").getBytes()); 
		toClient.write(("\r\n").getBytes());
		System.out.println("--------------------------------------------------------");
		System.out.println("status_code: " + status_code);
		System.out.println("Content-Type: " + mimeTypes.get(mimeType));
		System.out.println("Content-Length: " + content_length);
	}
	
	private static void responseData(String status_code, String response, BufferedOutputStream toClient, boolean isFile, boolean isHead, boolean isRedirct, String redirectURL){
		try {
			String mimeType = ""; 
			int content_length = 0; 
			
			if(isFile){
				String fileName = response; //get filename from response
				mimeType = fileName.substring(fileName.indexOf(".") + 1); //get mime type and content length
				byte[] fileBytes = null;
				
				// Read file
				try{
					Path path = Paths.get(FILEPATH, fileName);
					fileBytes = Files.readAllBytes(path);
					content_length = fileBytes.length;
				}catch(IOException e){
					status_code = "500 Internal Server Error";
				}finally{
					// write to client
					responseHeader(status_code, mimeType, content_length, toClient, isRedirct, redirectURL);
					if(!isHead){
						toClient.write(fileBytes);
					}
				}					
			}else{
				content_length = response.length();
				responseHeader(status_code, mimeType, content_length, toClient, isRedirct, redirectURL);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	

	public static void main(String[] args) throws Exception{
		if(args.length != 1 || args[0].length() < 13){
			System.out.println("Usage: java .classfile --serverPort=1234");
			System.exit(-1);
		}
		
		int port = Integer.parseInt(args[0].split("=")[1]);
		ServerSocket serverSocket = new ServerSocket(port);
		
		//load redirect map
		redirectMap = new Hashtable<String, String>();
		String defMap = "www/redirect.defs";
		String item = "";
		try{
			FileReader fr = new FileReader(defMap);
			BufferedReader br = new BufferedReader(fr);
			while((item = br.readLine()) != null){
				String[] data = item.split(" ");
				if(!redirectMap.containsKey(data[0])){
					redirectMap.put(data[0], data[1]);
				}
			}
		}catch(FileNotFoundException e){
			e.printStackTrace();
		}
		
		// Handle multiple requests
		while(true) {
			Socket connection = serverSocket.accept();
			new Thread(new RequestHandler(connection)).start();
		}
	}

}

