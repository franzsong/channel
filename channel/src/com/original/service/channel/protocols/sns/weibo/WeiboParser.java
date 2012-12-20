package com.original.service.channel.protocols.sns.weibo;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
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

import weibo4j.model.Emotion;
import weibo4j.model.Source;
import weibo4j.model.Status;
import weibo4j.model.WeiboException;

import com.original.service.channel.ChannelMessage;
import com.original.service.channel.Constants;

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
	
	private static SimpleDateFormat weiboFormat = 
			new SimpleDateFormat("MM月dd日 HH:mm");
	
	public static final String PREFIX_EMOTION = "emotion_";
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
					+ parseDateAndSource(msg);
		}
		return "";
	}
	public static String parseIgnoreImage(ChannelMessage msg) {
		if(msg != null) {
			return parse(msg.getBody() + "<br>")
//					+ parseImageText(msg) 
					+ parseDateAndSource(msg);
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
				try {
					String copy = text;
					if(copy != null && copy.length() > 0) {
						Matcher matcher = Pattern.compile("\\[(.+?)\\]").matcher(copy);//注意这里必须使用"非贪婪模式"
						String phrase = null;
						while(matcher.find()) {
							phrase = matcher.group();
							copy = copy.replace(phrase, findEmotions(phrase));
						}
					}
					return copy;
				}
				finally {
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
		if(phrase != null && phrase.matches("\\[.+?\\]")) {
			Object obj = cache.get(PREFIX_EMOTION+phrase);
			if(obj != null) 
				return (String)obj;
			
			for(Emotion emotion : emotions) {
				if(emotion.getPhrase().equals(phrase)) {
					obj = emotion.getUrl();
					
					String url = String.format("<img border=\"0\" src=\"%s\" alt=\"%s\" />", obj, phrase);
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
	 * @param status 微博消息对象
	 * @return
	 */
	public static String parseImageText(Status status){
		String imgUrl = "";
		if (!"".equals(status.getThumbnailPic())) {
			imgUrl =  "<br/><br/><a href=\"" + status.getBmiddlePic() 
				+ "\"><img border=\"0\" src=" + status.getThumbnailPic()
				+ " /></a>";
		}
		return imgUrl;
	}
	public static String parseImageText(ChannelMessage msg) {
		HashMap exts = msg.getExtensions();
		String imgUrl = "";
		if(exts == null || exts.isEmpty())
			return imgUrl;
		
		String thumbNailPic = (String)exts.get(Weibo_ThumbNail_Pic),
				middlePic = (String)exts.get(Weibo_Middle_Pic);
		if (!"".equals(thumbNailPic)) {
			imgUrl =  "<br/><br/><a href=\"" + middlePic
				+ "\"><img border=\"0\" src=" + thumbNailPic
				+ " /></a>";
		}
		return imgUrl;
	}
	
	/**
	 * 获取微博中图片的高度，用于界面显示
	 * @param imgURL
	 * @return
	 */
	public static int  getImageHeight(String imgURL) {
		java.net.URL url = null;
		int height = 0;
		if(imgURL == null || !imgURL.startsWith("http")) {
			return height;
		}
		
		try {
			url = new URL(imgURL);
		} catch (MalformedURLException e) {
			System.err.println("载入图片失败 MalformedURLException");
		}
		
		// TODO BufferedImage 子类描述具有可访问图像数据缓冲区的 Image
		// 使用BufferedImage 才能再未显现图片时知道图片的大小
		BufferedImage bi = null;
		try {
			bi = javax.imageio.ImageIO.read(url);
			height = bi.getHeight() ;
			bi = null;
		} catch (IOException e) {
			System.err.println("载入图片失败 IOException");
		} catch (java.lang.IllegalArgumentException e) {
			System.err.println("载入图片失败 IllegalArgumentException");
		}
		return height;
	}
	public static int getImageHeight(ChannelMessage msg) {
		HashMap exts = msg.getExtensions();
		String imgUrl = "";
		if(exts!= null && !exts.isEmpty()) {
			imgUrl = (String)exts.get(Weibo_ThumbNail_Pic);
		}
		return getImageHeight(imgUrl);
	}
	
	/**
	 * 获取微博的时间+来源
	 * @param status
	 * @return
	 */
	public static String parseDateAndSource(Status status){
		String dateAndSource = "<br/><br/>"
			+ weiboFormat.format(status.getCreatedAt()) + "  来自";
		
		Source source = status.getSource();// 来源
		dateAndSource += "<a href=\"" + source.getUrl() + "\">"
			+ source.getName() + "</a>";
		return dateAndSource;
	}
	public static String parseDateAndSource(ChannelMessage msg){
		HashMap exts = msg.getExtensions();
		String sourceUrl = null,
				sourceName = null;
		if(exts != null && !exts.isEmpty()) {
			sourceUrl = (String)exts.get(Weibo_SOURCE_URL);
			sourceName = (String)exts.get(Weibo_SOURCE_NAME);
		}
		
		if(sourceUrl != null && sourceName != null) {
			String dateAndSource = "<br/><br/>"
					+ weiboFormat.format(msg.getRecievedDate()) + "  来自";
				
				dateAndSource += "<a href=\"" + sourceUrl + "\">"
					+ sourceName + "</a>";
				return dateAndSource;
		}
		return "";
	}
	
	/**
	 * 返回带统计信息的标签文本(如转发数和微博数)
	 * @param text 标签文本
	 * @param count 统计数
	 * @return
	 */
	public String parseCountText(String text, int count) {
		return (text == null ? "" : text) +
				(count == 0 ? "" : "(" + count + ")");
	}
}
