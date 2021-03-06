/**
 * com.original.app.emai.ReceiveEmail.java
 *
 * Copyright (c) 2012, Original and/or its affiliates. All rights reserved.
 * ORIGINAL PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.original.service.channel.protocols.email.services;

import java.security.Security;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.URLName;
import javax.mail.internet.MimeMessage;

import org.apache.log4j.Logger;

import com.original.service.channel.Attachment;
import com.original.service.channel.ChannelAccount;
import com.original.service.channel.ChannelMessage;
import com.original.service.channel.Constants;
import com.original.service.channel.core.ChannelService;
import com.original.service.channel.core.MessageManager;
import com.original.service.channel.event.MessageEvent;
import com.original.service.channel.protocols.email.model.EMail;
import com.original.service.channel.protocols.email.model.EMailAttachment;
import com.original.service.channel.protocols.email.model.EMailParser;
import com.original.service.channel.protocols.email.vendor.EMailConfig;
import com.original.service.channel.protocols.email.vendor.EMailServer;
import com.original.util.log.OriLog;

/**
 * 
 * @author Admin
 */
public class EmailReceiver {

	Logger log = OriLog.getLogger(this.getClass());
	MailAuthentication account = null;
	private Store store;
	private Folder folder;
	final String SSL_FACTORY = "javax.net.ssl.SSLSocketFactory";
	private EmailReceiveThread backgroud;
	private HashMap<String, Boolean> cacheMsg;//cache message id
	private EmailService emailService;
	private ChannelAccount ca;
	
	@Deprecated
	public EmailReceiver(String name)
	{
		
	}
	public EmailReceiver(ChannelAccount ca, EmailService es) {

		this.ca = ca;
		this.account = new MailAuthentication("", ca.getAccount().getUser(), ca
				.getAccount().getPassword(), false);
		cacheMsg = new HashMap<String, Boolean>();
		backgroud = new EmailReceiveThread(this);
		emailService = es;
	}
	/**
	 * 后台线程启动。
	 */
	public void start()
	{
		backgroud.start();
	}

	/**
	 * 
	 * @param _account
	 */
	public EmailReceiver(MailAuthentication _account) {

		this.account = _account;
	}

	/**
	 * 
	 * @param _account
	 */
	public EmailReceiver(EmailSender sevice, MailAuthentication _account) {
		// this.sevice = sevice;
		this.account = _account;
	}

	public boolean hasAccount() {
		if (account == null) {
			return false;
		}
		return true;
	}

	/**
	 * 
	 * @return
	 */
	public boolean receive() {
		log.debug("receive email from " + account.getUserName() + "!");
		try {
			return recceiveMessages();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (folder != null) {
					folder.close(true);
					store.close();
				}
			} catch (Exception e) {
			}
		}		
		return false;
	}
	/**
	 * 
	 * @return
	 */
	private boolean  recceiveMessages() {
		
		Properties props = new Properties();
		EMailServer server = EMailConfig.getEMailConfig().getEMailServerByUser(
				account.getUserName());
		if (server == null) {
			return false;
		}
		String mailType = server.getMailtype().value();
		if (!"none".equalsIgnoreCase(server.getSecurityprotocol().value())) {
			Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());
			props.setProperty("mail." + mailType.toLowerCase()
					+ ".socketFactory.class", SSL_FACTORY);
			props.setProperty("mail." + mailType.toLowerCase()
					+ ".socketFactory.fallback", "false");
			props.setProperty("mail." + mailType.toLowerCase() + ".port", ""
					+ server.getPopport());
			props.setProperty("mail." + mailType.toLowerCase()
					+ ".socketFactory.port", "" + server.getPopport());
		}
		Session session = Session.getInstance(props, null);
		URLName urln = new URLName(mailType, server.getPopserver(),
				server.getPopport(), null, account.getUserName(),
				account.getPassword());
		Message[] msgs = null;
		try {
			store = session.getStore(urln);
			store.connect();
			folder = store.getFolder("INBOX");
			folder.open(Folder.READ_WRITE);
			msgs = folder.getMessages();
			
			//消息解析成MailMessage ，然后再转化成 Message，发现给外部
			if (msgs != null && msgs.length > 0)
			{
				parse(msgs);
			}
			return true;
		} catch (Exception ex) {
			return false;
		}
	}
	
    private String getRandomMessageID()
	{
		long millis = System.currentTimeMillis();
		UUID idOne = UUID.randomUUID();
		return millis + "$" + idOne;
	}
    
	private void parse(Message[] msgs) {
		if (msgs == null || msgs.length <= 0) {
			return;
		}
		ChannelService csc = ChannelService.getInstance();
		MessageManager msm = csc.getMsgManager();
		
		EMailParser parser = new EMailParser("cydow");
		for (int i = 0; i < msgs.length; i++) {
			try {
				String newMsgId = ((MimeMessage) msgs[i]).getMessageID();
				if (newMsgId == null)
				{
					newMsgId = getRandomMessageID();
				}
				boolean existing = cacheMsg.containsKey(newMsgId);
				// 已经放入池内，并且已经解析完成。
				//这一步减少数据库的查询工作
				if (existing && cacheMsg.get(newMsgId)) {
					continue;
				}
				//检查是否存库内
				if (msm.isExist(newMsgId)){	
					continue;
				}
				//邮件的消息			
				EMail email = parser.parseMessage((MimeMessage) msgs[i],
						account.getUserName(), "inbox", (String) null);
				ChannelMessage[] cmsg = new ChannelMessage[1];
				//邮件的消息转换为渠道的消息
				cmsg[0] = mailMessage2Message(email);
				MessageEvent evt = new MessageEvent(null, null,MessageEvent.Type_Added, cmsg, null,null);
				emailService.fireMessageEvent(evt);
				// 解析完毕，内存缓存。
				cacheMsg.put(newMsgId, Boolean.TRUE);
				
				//保存联系人
				emailService.parsePeople(cmsg[0],true);

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	private ChannelMessage mailMessage2Message(EMail email)
	{		
		ChannelMessage msg = new ChannelMessage();
		msg.setMessageID(email.getMsgId());
		
		msg.setBody(email.getContent());
		msg.setChannelAccount(ca);
		msg.setSize(email.getSize());
		//MIME Type Tables		
		msg.setType(ChannelMessage.TYPE_RECEIVED);
		msg.setContentType(Constants.Content_Type_Text_Html);
		msg.setClazz(ChannelMessage.MAIL);
		
		HashMap<String, Integer> flags = new HashMap<String, Integer>();
		flags.put(ChannelMessage.FLAG_REPLYED,	email.getIsReplay());
		flags.put(ChannelMessage.FLAG_SIGNED, email.getIsSign());
		flags.put(ChannelMessage.FLAG_SEEN,	email.getIsRead());
		flags.put(ChannelMessage.FLAG_DELETED, email.getIsDelete());
		flags.put(ChannelMessage.FLAG_PROCESSED,	email.getIsProcess());
		flags.put(ChannelMessage.FLAG_TRASHED, email.getIsTrash());
		msg.setFlags(flags);		
		msg.setFollowedID(null);
		msg.setFromAddr(email.getAddresser());
		
		HashMap<String, String> exts = new HashMap<String, String>();
		exts.put(ChannelMessage.EXT_EMAIL_BCC, email.getBcc());
		exts.put(ChannelMessage.EXT_EMAIL_CC, email.getCc());
		exts.put(ChannelMessage.EXT_EMAIL_ReplyTo, email.getReplayTo());
		exts.put(ChannelMessage.EXT_EMAIL_Foler, email.getType());
		msg.setExtensions(exts);
		msg.setSubject(email.getMailtitle());
		msg.setToAddr(ca.getAccount().getUser());
		
		msg.setReceivedDate(email.getSendtime());
		
		//parse Attachments.
		parseAttachments(msg, email);
		return msg;
	}
	
	/**
	 * Parse Email attachment to channel message.
	 * @return
	 */
	private void parseAttachments(ChannelMessage chmsg, EMail email)
	{
		List<EMailAttachment> emailAtts = email.getAttachments();
		List<Attachment> atts = new ArrayList<Attachment>();
		for (EMailAttachment ea : emailAtts)
		{
			atts.add(parseAttachment(ea));
		}
		chmsg.setAttachments(atts);
	}
	
	/**
	 * Parse Email attachment to channel message.
	 * @return
	 */
	private Attachment parseAttachment(EMailAttachment ea)
	{
		Attachment att = new Attachment();
		att.setContentId(ea.getCId());
		att.setFileId(ea.getFileID());
		att.setFileName(ea.getFileName());
		att.setSize(ea.getSize()==null?0:ea.getSize().intValue());
		att.setType(ea.getType());
		att.setFilePath(ea.getCDir());
		att.setContentType(ea.getCType());//attachment inline
		return att;
		
	}


}
