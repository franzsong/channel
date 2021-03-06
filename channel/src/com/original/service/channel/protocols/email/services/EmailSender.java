/**
 * com.original.app.emai.EmailService.java
 * 
 * Copyright (c) 2012, Original and/or its affiliates. All rights reserved.
 * ORIGINAL PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.original.service.channel.protocols.email.services;

import java.io.File;
import java.net.URI;
import java.security.Security;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;

import org.apache.log4j.Logger;
import org.bson.types.ObjectId;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.mongodb.gridfs.GridFSDBFile;
import com.original.service.channel.Attachment;
import com.original.service.channel.ChannelAccount;
import com.original.service.channel.ChannelMessage;
import com.original.service.channel.protocols.email.model.EMail;
import com.original.service.channel.protocols.email.model.EMailAttachment;
import com.original.service.channel.protocols.email.vendor.EMailConfig;
import com.original.service.channel.protocols.email.vendor.EMailServer;
import com.original.service.storage.GridFSUtil;
import com.original.util.log.OriLog;

/**
 *    How to send an attachment
    1 attach a file with path
    2 new DataSource and setDataHandler
    public void attachFile(File file) throws IOException, MessagingException {
    	FileDataSource fds = new FileDataSource(file);   	
        this.setDataHandler(new DataHandler(fds));
        this.setFileName(fds.getName());
    }
 
 * @author Admin
 * EMailSaver EmailSender
 */
public class EmailSender{// extends AbstractProcessingResource {

    Logger log = OriLog.getLogger(this.getClass());
    final String SSL_FACTORY = "javax.net.ssl.SSLSocketFactory";
    final String workdir = System.getProperty("user.dir") + "/temp/";
    private MimeMessage mimeMsg;
    private MimeMultipart mp;
    private String messageid = "";
    private MailAuthentication mailAccount = null;
    private String userId = "";
    private List<String> tempfiles = new ArrayList<String>();
    public EMailConfig config = EMailConfig.getEMailConfig();
//    MailServerMonitor monitor;
    /**
     * 
     * @param uid
     * @param ca
     */
    EmailSender(String uid, ChannelAccount ca)
    {
    	this.userId = uid;
    	this.mailAccount = new MailAuthentication("", ca.getAccount().getUser(), ca.getAccount().getPassword(), false);        
    }

//    public EmailSender(String _userId) {
//        userId = _userId;
//        mailAccount = new MailAuthentication("Song XueYong", "franzsoong@gmail.com", "syzb1234", false);
//    }
    
	public void start() {

	}

    /**
     *
     * @return
     */
    public MailAuthentication getMailAccout() {
        return this.mailAccount;
    }

    /**
     * set properties
     */
    private Properties getProperties(EMailServer server) {
        if (server == null) {
            return null;
        }

        Properties props = new Properties();
        if (server.isIssmtpauth()) {
            props.put("mail.smtp.auth", "true");
        } else {
            props.put("mail.smtp.auth", "false");
        }
        props.put("mail.smtp.host", server.getSmtpserver());
        props.put("mail.Transport.protocol", "smtp");
        props.put("mail.smtp.port", "" + server.getSmtpport());

        if ("ssl".equalsIgnoreCase(server.getSecurityprotocol().value())) {
            Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());
            props.put("mail.smtp.socketFactory.class", SSL_FACTORY);
            props.put("mail.smtp.socketFactory.port", "" + server.getSmtpport());
            props.put("mail.smtp.socketFactory.fallback", "false");
        } else if ("tls".equalsIgnoreCase(server.getSecurityprotocol().value())) {
            props.put("mail.smtp.starttls.enable", "true");
        }
        return props;
    }

    /**
     * 
     * @return
     */
    private String initMessage() {
        mimeMsg = new MimeMessage((Session) null);
        mp = new MimeMultipart("related");
        try {
            mimeMsg.saveChanges();
            messageid = mimeMsg.getMessageID().substring(1, mimeMsg.getMessageID().indexOf(".JavaMail"));
            mp.setSubType("related;type=\"multipart/alternative\"");
            mimeMsg.addHeader("X-Mailer", "DC Mail Sender 2.0");
        } catch (MessagingException ex) {
            log.error(OriLog.logStack(ex));
            return "initMessage:" + ex.getMessage();
        }
        return null;
    }

    private String setSubject(String mailSubject) {
        try {
            mimeMsg.setSubject(mailSubject);
        } catch (Exception e) {
            log.error(OriLog.logStack(e));
            return "Subject:" + e.getMessage();
        }
        return null;
    }

    /**
     * 改变文件内容中嵌入图片的路径
     */
    private String changeDir(List fileList, String content) {
        Document doc = Jsoup.parse(content);
        Elements media = doc.select("img");
        if (media == null || media.isEmpty()) {
            return content;
        }
        for (int i = 0; i < media.size(); i++) {
            Element a = (Element) media.get(i);
            String filename = a.attr("src");//获取本地图片路径
            if(filename.startsWith("file:")) {
            	fileList.add(filename);
            	a.removeAttr("src");
            	a.attr("src", "cid:Part" + i + "." + messageid);
            }
        }
        return doc.html();
    }

    /**
     * 设置邮件体
     */
    private String setBody(String mailBody) {
        try {
            List<String> fileList = new ArrayList<String>();
            String body = changeDir(fileList, mailBody);
            MimeMultipart mp1 = new MimeMultipart("alternative");
            MimeBodyPart part2 = new MimeBodyPart();
            part2.setContent(body, "text/html;charset=gb2312");
            part2.setHeader("Content-Transfer-Encoding", "base64");
            part2.setDisposition(MimeBodyPart.INLINE);
            mp1.addBodyPart(part2);
            MimeBodyPart text = new MimeBodyPart();
            text.setContent(mp1);
            mp.addBodyPart(text);
            if (fileList.size() > 0) {
                for (int i = 0; i < fileList.size(); i++) {
                    MimeBodyPart part3 = new MimeBodyPart();
                    File file = new File(new URI(fileList.get(i).toString()));
                    DataSource source = new FileDataSource(file);
                    part3.setDataHandler(new DataHandler(source));
                    part3.setFileName(MimeUtility.encodeText(source.getName(), "UTF-8", "B"));
                    part3.setContentID("<" + "Part" + i + "." + messageid + ">");
                    part3.setDisposition(MimeBodyPart.ATTACHMENT);
                    mp.addBodyPart(part3);
                }
            }
        } catch (Exception e) {
            log.error(OriLog.logStack(e));
            return "Body:" + e.getMessage();
        }
        return null;
    }


    private String setFrom(String from) {
        try {
            mimeMsg.setFrom(new InternetAddress(from));
        } catch (Exception e) {
            log.error(OriLog.logStack(e));
            return "From:" + e.getMessage();
        }
        return null;
    }

    private String setTo(String to) {
        if (to == null) {
            return null;
        }
        to = to.trim().replace(",", ";");
        if (to.length() == 0) {
            return null;
        }
        String[] receiverAddress = to.split(";");
        try {
            if (receiverAddress.length > 0) {
                InternetAddress[] address = new InternetAddress[receiverAddress.length];
                for (int i = 0; i < receiverAddress.length; i++) {
                    address[i] = new InternetAddress(receiverAddress[i]);
                }
                mimeMsg.addRecipients(Message.RecipientType.TO, address);
            }
        } catch (Exception e) {
            log.error(OriLog.logStack(e));
            return "To:" + e.getMessage();
        }
        return null;
    }

    private String setCopyTo(String copyto) {
        if (copyto == null) {
            return null;
        }
        copyto = copyto.trim().replace(",", ";");
        if (copyto.length() == 0) {
            return null;
        }
        String[] copyAddress = copyto.split(";");
        try {
            if (copyAddress.length > 0) {
                InternetAddress[] address = new InternetAddress[copyAddress.length];
                for (int i = 0; i < copyAddress.length; i++) {
					String addr = copyAddress[i].trim();
					if (!addr.isEmpty()) { //注意去掉前后空格
						address[i] = new InternetAddress(addr);
					}
                }
                mimeMsg.addRecipients(Message.RecipientType.CC, address);
            }
        } catch (Exception e) {
        	e.printStackTrace();
            return "CC:" + e.getMessage();
        }
        return null;
    }

    private String setBccTo(String bcc) {
        if (bcc == null) {
            return null;
        }
        bcc = bcc.trim().replace(",", ";");
        if (bcc.length() == 0) {
            return null;
        }
        String[] bccAddress = bcc.split(";");
        try {
            if (bccAddress.length > 0) {
                InternetAddress[] address = new InternetAddress[bccAddress.length];
                for (int i = 0; i < bccAddress.length; i++) {
                	String addr = bccAddress[i].trim();
					if (!addr.isEmpty()) { //注意去掉前后空格
						address[i] = new InternetAddress(addr);
					}
                }
                mimeMsg.addRecipients(Message.RecipientType.BCC, address);
            }
        } catch (Exception e) {
            log.error(OriLog.logStack(e));
            return "BCC:" + e.getMessage();
        }
        return null;
    }

    private String saveContent() {
        try {
            mimeMsg.setSentDate(new Date());
            mimeMsg.setContent(mp);
            mimeMsg.saveChanges();

        } catch (MessagingException ex) {
            log.error("产生异常:" + OriLog.logStack(ex));
            return "Save:" + ex.getMessage();
        }
        return null;
    }

    /**
     * 
     * @param receivers
     * @param copyTo
     * @param bccTo
     * @param title
     * @param content
     * @param attachs 
     * @param from
     * @return
     */
//    private String createMessage(EMail email) {
//        String receivers = email.getReceiver();
//        String copyTo = email.getCc();
//        String bccTo = email.getBcc();
//        String title = email.getMailtitle();
//        String content = email.getContent();
//        StringBuilder attachs = new StringBuilder();
//        String sender = email.getMailname();
//        content = atts2TempFiles(email, content, attachs);
//        return this.createMessage(receivers, copyTo, bccTo, title, content, attachs.toString(), sender);
//    }

	private String atts2TempFiles(EMail email, String content,
			StringBuilder attachs) {
		if (email.getAttachment() != null && email.getAttachments().size() > 0) {// franz pending
            List<EMailAttachment> attachments = email.getAttachments();//get_List();
            for (EMailAttachment attach : attachments) {
                String emailid = email.get_id();
                StringBuilder buffer = new StringBuilder();
                if (emailid == null) {
                    buffer.append(workdir).append(attach.getFileName());
                } else {
                    buffer.append(workdir).append(emailid).append("/").append(attach.getFileName());
                }
                String filename = buffer.toString();
//                content = saveAttachmentToTemp(content, attach, filename);
                attachs.append(filename).append(";");
            }
        }
		return content;
	}
	
	/**
	 * 
	 * @param receivers
	 * @param copyTo
	 * @param bccTo
	 * @param title
	 * @param content
	 * @param attachs
	 * @param from
	 * @return
	 */
    private String createMessage(String receivers, String copyTo, String bccTo, String title,
            String content, List<Attachment> attachs, String from) {
        String isSuccess = this.initMessage();
        if (isSuccess == null) {
            isSuccess = this.setFrom(from);
        }
        if (isSuccess == null) {
            isSuccess = this.setSubject(title.trim());
        }
        if (isSuccess == null) {
            isSuccess = this.setBody(content.trim());
        }
        if (isSuccess == null) {
            isSuccess = this.setTo(receivers);
        }
        if (isSuccess == null) {
            isSuccess = this.setCopyTo(copyTo);
        }
        if (isSuccess == null) {
            isSuccess = this.setBccTo(bccTo);
        }
        if (isSuccess == null) {
            isSuccess = this.addFileAtts(attachs);
        }
        if (isSuccess == null) {
            isSuccess = this.saveContent();
        }
        return isSuccess;
    }

    ///////////////////New for send email ////////////////////////////////
    
    /**
    *
    * @param receivers
    * @param copyTo
    * @param bccTo
    * @param title
    * @param content
    * @param attachs
    * @return
    */
    @Deprecated
   private boolean send(ChannelMessage chmsg, String receivers, String copyTo, String bccTo, String title, String content) {
       String isSuccess = this.createMessage(receivers, copyTo, bccTo, title, content, mailAccount.getUserName());
       if (isSuccess == null) {
           isSuccess = send(mailAccount);
       }
       if (isSuccess != null) {
           log.error(isSuccess);
       }
       addFileAtts(chmsg.getAttachments());
       return (isSuccess == null ? true : false);
   }
   /**
    * 
    * @param receivers
    * @param copyTo
    * @param bccTo
    * @param title
    * @param content
    * @param from
    * @return
    */
   private String createMessage(String receivers, String copyTo, String bccTo, String title,
           String content, String from) {
       String isSuccess = this.initMessage();
       if (isSuccess == null) {
           isSuccess = this.setFrom(from);
       }
       if (isSuccess == null) {
           isSuccess = this.setSubject(title.trim());
       }
       if (isSuccess == null) {
           isSuccess = this.setBody(content.trim());
       }
       if (isSuccess == null) {
           isSuccess = this.setTo(receivers);
       }
       if (isSuccess == null) {
           isSuccess = this.setCopyTo(copyTo);
       }
       if (isSuccess == null) {
           isSuccess = this.setBccTo(bccTo);
       }
       if (isSuccess == null) {
           isSuccess = this.saveContent();
       }
       return isSuccess;
   }
   
  /**
   * main interface for outer
   * @param msg
   * @return
   */
    public void send(ChannelMessage msg) {
    	String to = msg.getToAddr();
//    	String from = msg.getFromAddr();
    	HashMap<String, String> exts = msg.getExtensions();
    	
    	msg.setSentDate(new Date());
    	String bccTo = null, ccTo = null;
    	if (exts != null)
    	{
    		ccTo= exts.get(ChannelMessage.EXT_EMAIL_CC);
    		bccTo = exts.get(ChannelMessage.EXT_EMAIL_BCC);
    	}
    	String content = msg.getBody();
    	String title = msg.getSubject();
    	this.send(to, ccTo, bccTo, title, content, msg.getAttachments());    	//注意这里的抄送不应该用fromAddr，待以后由用户自己输入，暂时设为null。
    }
    Transport transport;
    /**
     *
     * @param _auth 
     * @return
     */
    public String send(MailAuthentication _auth) {
    	
    	try {
    		if (transport == null)
    		{
    			log.debug("账户信息=" + _auth.getUserName());
    			EMailServer server = config.getEMailServerByUser(mailAccount.getUserName());
    			Properties props = getProperties(server);
    			transport = null;
    			try {
    				MailAuthentication auth = null;
    				if (server != null && server.isIssmtpauth()) {
    					auth = _auth;
    				}
    				Session session = Session.getInstance(props, auth);
    				transport = session.getTransport("smtp");

//    				transport.addConnectionListener(monitor);
//    				transport.addTransportListener(monitor);

    				transport.connect();
    			} catch (Exception e2) {
    				log.debug(e2.getMessage());
    				return "连接服务器时错误！";
    			}
    		}
    		else
    		{
    			if (!transport.isConnected())
    			{
    				transport.connect();
    			}
    		}


    		log.debug("正在发送邮件....");
    		mimeMsg.setHeader("cydow_id", "xw385w0q9t8");
            transport.sendMessage(mimeMsg, mimeMsg.getAllRecipients());
            
            transport.close();
            log.debug("发送邮件成功!");
        } catch (Exception e) {
            log.error(OriLog.logStack(e));
            log.debug("发送邮件失败!");
            return "Send:" + e.getMessage();
        } finally {
            this.deleteTempfile();
        }
        return null;
    }
    
    

    /**
     *
     * @param recevier
     * @param title
     * @param content
     * @return
     */
    @Deprecated
    private boolean send(String recevier, String title, String content) {
        return send(recevier, null, null, title, content, null);
    }

    /**
     *
     * @param receivers
     * @param title
     * @param content
     * @param attachments
     * @return
     */
    @Deprecated
    private boolean send(String receivers, String title, String content, List<Attachment> attachments) {
        return send(receivers, null, null, title, content, attachments);
    }

    /**
     *
     * @param auth
     * @param receivers
     * @param title
     * @param content
     * @param attachments
     * @return
     */
//    @Deprecated
//    private boolean send(HashMap auth, String receivers, String title, String content, String attachments) {
////        EMailConfig.loadConfig(null);
//        String username = (String) auth.get("username");
//        String password = (String) auth.get("password");
//        String userid = (String) auth.get("userId");
//        MailAuthentication auth1 = new MailAuthentication(userid, username, password, false);
//        String isSuccess = this.createMessage(receivers, null, null, title, content, attachments, username);
//        if (isSuccess == null) {
//            isSuccess = send(auth1);
//        }
//        if (isSuccess != null) {
//            log.error(isSuccess);
//        }
//        return (isSuccess == null ? true : false);
//    }

//    private String saveAttachmentToTemp(String content, EMailAttachment eatt, String filename) {
//        StreamData sd = FileManager.fetchBinaryFile(new String(eatt.getData()));
//        String content1 = content;
//        if (eatt.getCId() != null) {
//            content1 = content.replace("cid:" + eatt.getCId(), filename);
//            try {
//                log.debug("Write attachment to temp directory (" + filename + ").....");
//                sd.writeToFile(filename);
//                tempfiles.add(filename);
//            } catch (Exception ex) {
//                log.error(OriLog.logStack(ex));
//            }
//        } else {
//            if (sd != null) {
//                FileExchange.getInstance().addContextVariable(filename, sd);
//            }
//        }
//        return content1;
//    }

    /**
     *
     * @param receivers
     * @param copyTo
     * @param bccTo
     * @param title
     * @param content
     * @param attachs
     * @return
     */
    
    private boolean send(String receivers, String copyTo, String bccTo, String title, String content, List<Attachment> attachs) {
        String isSuccess = this.createMessage(receivers, copyTo, bccTo, title, content, attachs, mailAccount.getUserName());
        if (isSuccess == null) {
            isSuccess = send(mailAccount);
        }
        if (isSuccess != null) {
        	System.err.println(isSuccess);
            log.error(isSuccess);
        }
        return (isSuccess == null ? true : false);
    }

    /**
     * 
     * @return
     */
    private MimeMessage getMimeMsg() {
        return mimeMsg;
    }

    private void deleteTempfile() {
        for (String filename : tempfiles) {
            File f = new File(filename);
            f.delete();
        }
        tempfiles.clear();
    }
    
    
    ////////////////发送附件///////////////////
    
    //transfer FS to temp file
    private void atts2TempFiles(ChannelMessage chmsg) {
    	List<Attachment> atts = chmsg.getAttachments();
    	if (atts != null)
    	{
    		for (Attachment att : atts)
    		{
    			//file path for send attachment
    			if (att.getFilePath() != null)
    			{
    				continue;
    			}
    			//had save to file system.
    			ObjectId fileId = att.getFileId();
    			GridFSDBFile dbfile = GridFSUtil.getGridFSUtil().getFile(fileId);
    			if (dbfile != null)
    			{
    				String filePath = workdir + att.getFileName();
    				try
    				{
    					GridFSUtil.getGridFSUtil().writeFile(fileId, filePath);
    					 tempfiles.add(filePath);
    					att.setFilePath(filePath);
    				}
    				catch(Exception exp)
    				{
    					exp.printStackTrace();
    				}
    			}    			
    		}
    	}
	}
    

    /**
     * 文件名称列表，转换成FileDataSource
     * (临时文件，可以改造DataSource([])不用临时文件）。
     * 添加附件
     */
    private String addFileAtts(List<Attachment> atts) {
        try {
            if (atts == null || atts.isEmpty()) {
                return null;
            }
            for (Attachment att : atts){
                if (att == null || att.getFilePath() == null) {
                    continue;
                }
                MimeBodyPart bp = new MimeBodyPart();
                if (att.getFilePath().startsWith("file:"))
                {
                	DataSource source = new FileDataSource(new File(new URI(att.getFilePath())) );
                	 bp.setDataHandler(new DataHandler(source));
                     bp.setFileName(MimeUtility.encodeText(source.getName(), "UTF-8", "B"));
                     mp.addBodyPart(bp);
                }
                else
                {
                	DataSource source = new FileDataSource(new File(att.getFilePath()) );
                	 bp.setDataHandler(new DataHandler(source));
                     bp.setFileName(MimeUtility.encodeText(source.getName(), "UTF-8", "B"));
                     mp.addBodyPart(bp);
                }
              
            }
        } catch (Exception e) {
            log.error(OriLog.logStack(e));       
            e.printStackTrace();
            return "Attach: " + e.getMessage();
        }
        return null;
    }
    
    

    
}