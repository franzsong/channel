package com.original.service.channel.protocols.sns.weibo;

import iqq.service.HttpService;
import iqq.util.Method;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringEscapeUtils;

import weibo4j.model.Emotion;
import weibo4j.model.WeiboException;

import com.original.service.channel.Attachment;
import com.original.service.channel.ChannelMessage;
import com.original.service.channel.Constants;
import com.original.service.storage.GridFSUtil;

/**
 * 微博处理消息类，用于将纯文本的Status对象，转换成Html格式的Message对象。
 * HTML显示样式尽量和sina weibo页面显示的效果一致。
 * @author WMS
 *
 */
public class WeiboParser implements Constants
{
	private static List<Emotion> emotions = new ArrayList<Emotion>();
	private static Map<String, Object> cache = new HashMap<String, Object>();
	
//	private static SimpleDateFormat weiboFormat = 
//			new SimpleDateFormat("MM月dd日 HH:mm");
	
	public static final String PREFIX_EMOTION = "emotion_";
	public static final String IMAGE_PATTERN = "<img .*src=\"@url\".*>";
	private static Lock parserLock = new ReentrantLock();
	
	/**
	 * 注册(添加)微博官方表情
	 * @param emotions
	 */
	public static void registerEmotions(List<Emotion> emotions) {
		if(emotions != null) {
			WeiboParser.emotions.addAll(emotions);
		}
	}
	
	public static String parse(ChannelMessage msg) {
		if(msg != null) {
			return parse(msg.getBody() + "<br>")
					+ parseImageText(msg) 
//					+ parseDateAndSource(msg)
					;
		}
		return "";
	}
	
	/**
	 * 对微博消息文本统一进行格式处理，包括超链、##符号、@符号等。以后再扩充
	 * @param text 微博消息文本
	 * @return
	 */
	private static String parse(String text) {
		//处理表情
		text = StringEscapeUtils.unescapeHtml(text);
		text =parseEmotions(text);
		//处理@
		text = text.replaceAll(
				"@([^<^>^,^，^ ^　^.^。^:^：^/^&^@^#^\"^\']{1,})([：, :/<])",
				"<a href=@$1>@$1</a>$2");
		//处理# #
		text = text.replaceAll("#(.+?)#",
				"<a href=#$1>#$1#</a>");
		//处理a超链
		text = text.replaceAll(
				"([^href=^href=\"])(http://[-a-zA-Z0-9:%_\\+.~#?&/=]{1,})",
				"$1<a href=$2>$2</a>");
		text = text
				.replaceAll("<a[^>]* href=\"\"[^>]*>([^<^>]*)</a>", "$1");
		text = text.replaceAll("<a[^>]* href=>([^<^>]*)</a>", "$1");
		return text;
	}
	
	/**
	 * 统一转换表情(这里做了锁控制，只有等{@link #collectionEmotions(String)}收集完表情后，才进行转换表情操作)
	 * @param text 表情字符
	 * @return
	 */
	public static String parseEmotions(final String text) {
		Callable<String> callable = new Callable<String>() {
			@Override
			public String call() throws Exception {
				parserLock.lock();
				List<String> cache = new ArrayList<String>();
				try {
					String copy = text;
					if(copy != null && copy.length() > 0) {
						Matcher matcher = Pattern.compile("\\[(.+?)\\]").matcher(copy);//注意这里必须使用"非贪婪模式"
						String phrase = null;
						while(matcher.find()) {
							phrase = matcher.group();
							if (cache.contains(phrase))
								continue;
							
							copy = copy.replace(phrase, findEmotions(phrase));
							cache.add(phrase);
						}
					}
					return copy;
				}
				finally {
					cache.clear();
					cache = null;
					parserLock.unlock();
				}
			}
		};
		
		FutureTask<String> futureTask = new FutureTask<String>(callable);
		new Thread(futureTask, "Parse Emotions").start();
		
		while(!futureTask.isDone()) {
			try {
				Thread.sleep(100);
			}catch(Exception ex) {
				return text;
			}
		}
		
		try {
			return futureTask.get();
		}
		catch(Exception ex) {
			return text;
		}
	}	
	
	/**
	 * 查找表情
	 * @param phrase 表情短语
	 * @return 表情的链接地址
	 */
	public static String findEmotions(String phrase) {
		if(phrase != null && phrase.matches("\\[(.+?)\\]")) {
			Object obj = cache.get(PREFIX_EMOTION+phrase);
			if(obj != null) 
				return (String)obj;
			
			for(Emotion emotion : emotions) {
				if(emotion.getPhrase().equals(phrase)) {
					obj = emotion.getUrl();
					
//					String url = String.format("<img border=\"0\" src=\"%s\" alt=\"%s\" />", obj, phrase);
					String url = String.format("<img border=\"0\" src=\"%s\" />", obj);
					cache.put(PREFIX_EMOTION+phrase, url);
					return url;
				}
			}
			cache.put(PREFIX_EMOTION+phrase, phrase);
		}
		return phrase;
	}
	
	/**
	 * 由授权Token获取微博官方表情
	 * @param token
	 * @throws WeiboException
	 */
	public static void collectionEmotions(final String token) throws WeiboException
	{
		new Thread(new Runnable()
		{
			public void run()
			{
				boolean isLock = false;
				try
				{
					if(isLock = parserLock.tryLock()) {
						weibo4j.Timeline timeline = new weibo4j.Timeline();
						timeline.setToken(token);
						WeiboParser.registerEmotions(timeline.getEmotions());
					}
				} catch (WeiboException ex)
				{
					ex.printStackTrace();
				} finally {
					if(isLock) parserLock.unlock();
				}
			}
		},"Collect Emotions").start();
	}
	
	/**
	 * 从微博中获取图片的链接地址，点击该链接地址可以放大图片
	 * @param msg 微博消息对象
	 * @return
	 */
	public static String parseImageText(ChannelMessage msg) {
		HashMap<String, String> exts = msg.getExtensions();
		String imgUrl = "";
		if(exts == null || exts.isEmpty())
			return imgUrl;
		
		String thumbNailPic = (String)exts.get(Weibo_ThumbNail_Pic),
				middlePic = (String)exts.get(Weibo_Middle_Pic);
		if (!"".equals(thumbNailPic)) {
			
			List<Attachment> attachs = msg.getAttachments();
			if(attachs != null && !attachs.isEmpty())
			{
				Attachment attach = attachs.get(0);//顶多只有一个
				File thumbNail = new File(WeiboService.SNS_WEIBO_THUMBNAIL_TEMPDIR, attach.getFileName());
				if(thumbNail.exists()) {//1、先读取本地：
					thumbNailPic  = thumbNail.toURI().toString();
				}
				else {//2、读取数据库
					try {
						GridFSUtil.getGridFSUtil().writeFile(attach.getFileId(), thumbNail);
						thumbNailPic  = thumbNail.toURI().toString();
					} catch (Exception ex) {
						
					}
				}
			}
			
			//3、最后如果上面两个都没有，只能读取http:
			Dimension picSize = getImageSize(thumbNailPic);//thumbNailPic既可能是Http://也可能是file://
			
			imgUrl = "<br/><a href=\"" + middlePic + "\">" +
					"<img border=\"0\" src=" + thumbNailPic + " width=\"" + picSize.width + "\" height=\"" + picSize.height + "\"/>" +
					"</a>";
		}
		return imgUrl;
	}
	
	/**
	 * 获取微博中图片的高度，用于界面显示
	 * @param imgURL
	 * @return
	 */
	private static Dimension  getImageSize(String imgURL) {
		Dimension size = new Dimension(32,32);
		if(imgURL == null ||
				(!imgURL.startsWith("http") && !imgURL.startsWith("file")) ) {
			return size;
		}
		
		// TODO BufferedImage 子类描述具有可访问图像数据缓冲区的 Image
		// 使用BufferedImage 才能再未显现图片时知道图片的大小
		BufferedImage bi = null;
		try {
			if(imgURL.startsWith("http")) {
				bi = javax.imageio.ImageIO.read(new URL(imgURL));
			}
			else if(imgURL.startsWith("file")) {
				bi =  javax.imageio.ImageIO.read(new File(new URI(imgURL)));
			}
			size.width = bi.getWidth() ;
			size.height = bi.getHeight();
			
		} catch (IOException e) {
			System.err.println("载入图片失败 IOException");
		} catch (java.lang.IllegalArgumentException e) {
			System.err.println("载入图片失败 IllegalArgumentException");
		} catch (URISyntaxException e) {
			System.err.println("载入图片失败 URISyntaxException");
		} finally {
			bi = null;
		}
		return size;
	}
	
//	/**
//	 * 获取微博的时间+来源
//	 * @param status
//	 * @return
//	 */
//	public static String parseDateAndSource(Status status){
//		String dateAndSource = "<br/><br/>"
//			+ weiboFormat.format(status.getCreatedAt()) + "  来自";
//		
//		Source source = status.getSource();// 来源
//		dateAndSource += "<a href=\"" + source.getUrl() + "\">"
//			+ source.getName() + "</a>";
//		return dateAndSource;
//	}
//	public static String parseDateAndSource(ChannelMessage msg){
//		HashMap exts = msg.getExtensions();
//		String sourceUrl = null,
//				sourceName = null;
//		if(exts != null && !exts.isEmpty()) {
//			sourceUrl = (String)exts.get(Weibo_SOURCE_URL);
//			sourceName = (String)exts.get(Weibo_SOURCE_NAME);
//		}
//		
//		if(sourceUrl != null && sourceName != null) {
//			String dateAndSource = "<br/><br/>"
//					+ weiboFormat.format(msg.getReceivedDate()) + "  来自";
//				
//				dateAndSource += "<a href=\"" + sourceUrl + "\">"
//					+ sourceName + "</a>";
//				return dateAndSource;
//		}
//		return "";
//	}
	
	//-------------------------------- 下面用于处理发送微博 --------------------------------//
	/**
	 * 提取微博内容中可能含有的图片地址
	 * @param content
	 * @return 
	 */
	public static String[] fetchImageURL(String content) {
		if(content != null && !content.isEmpty()) {
			String regex = Matcher.quoteReplacement(
					IMAGE_PATTERN.replace("@url", "((file:/|http://|https://).+?)"));
			Matcher matcher = Pattern.compile(regex).matcher(content);
			if(matcher.find()) {
				return new String[]{matcher.group(), matcher.group(1)};
			}
		}
		return null;
	}
	
	/**
	 * 处理微博内容(统一编码)，用于快速回复
	 * @param content
	 * @return
	 * @throws WeiboException
	 */
	public static String parseUTF8(String content) throws WeiboException{
//		try {
//            content = URLEncoder.encode(content, "UTF-8");
//        } catch (UnsupportedEncodingException e) {
//        }
		return content;
	}
	
	/**
	 * 处理微博内容(统一编码)，用于回复(回复内容比较丰富，如可以有图片等)
	 * @param msg 消息对象
	 * @param addAt 是否添加@符号，自己给自己发微博为false；其他情况为true。
	 * @return
	 * @throws WeiboException
	 */
	public static Object[] parseUTF8(ChannelMessage msg, boolean addAt) throws WeiboException
	{
		String content = null;
		Object[] items = null;
		if(msg != null && (content = msg.getBody()) != null)
		{
			//获取图片
			String[] imgURLs = fetchImageURL(content);
			
			items = new Object[2];
			if(imgURLs != null) {
				content = content.replace(imgURLs[0], "");//清除图片内容
				content = content.replaceAll("\r|\n", ""); //清除换行符
				items[1] = parseBytes(imgURLs[1]);
			}
			
			if (addAt) {
				items[0] = "//@" + msg.getToAddr() + ": " + parseUTF8( content);
			} else {
				items[0] = parseUTF8(content);
			}
		}
		return items;
	}
	
	/**
	 * 将输入流转换成字节数组，即将文件或网络流转换成字节数组
	 * @param in 输入流，可以使文件输入流，也可以是Http输入流
	 * @return
	 */
	public static byte[] parseBytes(InputStream in) {
		if (in != null) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			int length = 0;
			byte[] bytes = null;
			try {
				bytes = new byte[1024 * 10];
				while ((length = in.read(bytes)) != -1) {
					out.write(bytes, 0, length);
				}
				out.flush();
				
				bytes = out.toByteArray();
				return bytes;
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (in != null) { //关闭输入流
					try {
						in.close();
					} catch (IOException e) {
					}
				}
				if(out != null) { //关闭输出流
					try {
						out.close();
					} catch(IOException e) {
					}
				}
			}
		}
		return null;
	}
	public static byte[] parseBytes(File file) {
		try {
			return parseBytes(new FileInputStream(file));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	/**
	 * 将File://或者Http://这些URL地址对应的文件转换成字节数组
	 * @param url
	 * @return
	 */
	public static byte[] parseBytes(String url) {
		if(url != null && !url.isEmpty()) {
			if(url.startsWith("file:/")) { //文件
				try {
					return parseBytes(new File(new URI(url)));
				} catch (URISyntaxException ex) {
					// TODO Auto-generated catch block
					System.err.println(ex);
				}
			}
			else if(url.startsWith("http://") || url.startsWith("https://")) { //网络文件
				try {
					HttpService hs = new HttpService(url, Method.GET);
					return parseBytes(hs.getInputStream());
				}
				catch(Exception ex) {
					ex.printStackTrace(); //网络不通
				}
			}
		}
		return null;
    }	
}
