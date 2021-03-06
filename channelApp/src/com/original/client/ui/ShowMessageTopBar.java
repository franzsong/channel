package com.original.client.ui;

import java.awt.Dimension;
import java.text.SimpleDateFormat;

import com.original.channel.ChannelNativeCache;
import com.original.client.util.ChannelConstants;
import com.original.client.util.IconFactory;
import com.original.service.channel.ChannelMessage;
import com.seaglasslookandfeel.widget.SGLabel;

/**
 * 显示完整消息顶部栏，可以用于QQ、微博、邮件等消息的完整显示
 * @author WMS
 *
 */
public class ShowMessageTopBar extends ChannelMessageTopBar
{
	private SimpleDateFormat messageFormat = new SimpleDateFormat("MM月dd日 HH:mm");//消息时间格式
	private SGLabel messageHeader = new SGLabel();
	
	private ChannelMessage channelMsg = null;//当前显示消息对象
	
	public ShowMessageTopBar(ChannelMessage msg)
	{
		super(false);
		channelMsg = msg;
	}

	@Override
	protected void initStatusBar() {
		super.initStatusBar();
		if(channelMsg != null && channelMsg.getMessageID() != null)
		{
			if (channelMsg.isQQ())
			{
				messageHeader.setIcon(IconFactory.loadIconByConfig("readQQIcon"));
			} else if (channelMsg.isWeibo())
			{
				messageHeader.setIcon(IconFactory.loadIconByConfig("readWeiboIcon"));
			} else if (channelMsg.isMail())
			{
				messageHeader.setIcon(IconFactory.loadIconByConfig("readMailIcon"));
			}
			
			messageHeader.setForeground(ChannelConstants.LIGHT_TEXT_COLOR);
			messageHeader.setFont(ChannelConstants.DEFAULT_FONT);
			messageHeader.setText(channelMsg.getReceivedDate() == null ? "" : messageFormat.format(channelMsg.getReceivedDate()));
			messageHeader.setIconTextGap(10);
			messageHeader.setHorizontalTextPosition(SGLabel.RIGHT);
		}
	}

	@Override
	protected void constructStatusBar() {
		// TODO Auto-generated method stub
		setLayout(null); //空布局，从而精确定位控件
		setPreferredSize(new Dimension(SIZE.width-20, SIZE.height+15));
		
		Dimension dim = messageHeader.getPreferredSize();
		messageHeader.setBounds(10, 10, dim.width, dim.height);
		add(messageHeader);
	}

	@Override
	public void doClose() {
		ChannelDesktopPane desktop =ChannelNativeCache.getDesktop();
		if(channelMsg != null) {
			desktop.removeShowComp(PREFIX_SHOW + channelMsg.getContactName());
		}
		else {
			desktop.showDefaultComp();
		}
	}
	
}
