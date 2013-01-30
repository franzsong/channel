package com.original.channel;

import java.util.List;

import com.original.service.channel.Account;
import com.original.service.channel.core.ChannelService;
import com.original.service.channel.core.MessageManager;

/**
 * Channel访问服务，用于UI界面访问远程服务，操作数据库等。
 * @author WMS
 *
 */
public class ChannelAccesser
{
	static ChannelService cs = ChannelService.getInstance();
	
	/**
	 * 获取消息管理服务对象，即MongoDB数据管理服务对象，用于获取数据
	 * @return
	 */
	public static MessageManager getMsgManager() {
		return cs.getMsgManager();
	}
	
	public static ChannelService getChannelService() {
		return cs;
	}
	
	public static List<Account> getAccounts() {
		return cs.getAccounts();
	}
}