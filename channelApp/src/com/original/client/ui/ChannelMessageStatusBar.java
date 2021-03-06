package com.original.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.border.EmptyBorder;

import com.original.client.EventConstants;
import com.original.client.util.ChannelConfig;
import com.original.client.util.ChannelConstants;
import com.original.client.util.IconFactory;
import com.seaglasslookandfeel.widget.SGLabel;
import com.seaglasslookandfeel.widget.SGPanel;

/**
 * 消息Channel状态栏，目前状态栏主要汇总消息数(分类型，如微博、QQ的消息数)
 * 同时还有一个展开全部(body)的功能。
 * @author WMS
 *
 */
public class ChannelMessageStatusBar extends SGPanel implements PropertyChangeListener, EventConstants
{
	private ChannelMessageBodyPane body;
	static Dimension SIZE = new Dimension(
			ChannelConfig.getIntValue("msgBodyWidth"), 
			30);
	
	//消息类型数
	private int mailCount = 0,
			qqCount = 0,
			weiboCount = 0;
	
	private SGLabel mailLabel = new SGLabel("邮件：" + mailCount),
			qqLabel = new SGLabel("QQ：" + qqCount),
			weiboLabel = new SGLabel("微博：" + weiboCount);
	
	private SGLabel showAllLabel = new SGLabel("全部记录", 
			IconFactory.loadIconByConfig("expandAllIcon"), SGLabel.RIGHT);
	
	protected boolean hasNotify = false;
	protected Lock channelLock = new ReentrantLock();
	
	public void addNotify()
	{
		super.addNotify();
		if(!hasNotify) {
			initStatusBar();
			constructStatusBar();
			hasNotify = true;
		}
	}

	/**
	 * 初始化一些控件，如设置图标，颜色等。
	 */
	protected void initStatusBar() {
		Color color = ChannelConstants.LIGHT_TEXT_COLOR;//统一前景色
		mailLabel.setForeground(color);
		qqLabel.setForeground(color);
		weiboLabel.setForeground(color);
		
		//显示全部标签
		showAllLabel.setForeground(color);
		showAllLabel.setIconTextGap(5);
		showAllLabel.setHorizontalTextPosition(SGLabel.LEFT);
		showAllLabel.setCursor(ChannelConstants.HAND_CURSOR);
		showAllLabel.addMouseListener(new MouseAdapter()
		{
			public void mouseClicked(MouseEvent e)
			{
				doClick();
			}
		});
	}
	
	/**
	 * 构建消息Channel状态栏
	 */
	protected void constructStatusBar() {		
		setLayout(new BorderLayout()); //设置布局方式和面板大小
		setPreferredSize(SIZE);
		setBorder(new EmptyBorder(5,45,0,10));
		
		//开始添加控件
		SGPanel left = new SGPanel(new FlowLayout(FlowLayout.LEFT,10,0));
		left.add(mailLabel);
		left.add(qqLabel);
		left.add(weiboLabel);
		add(left, BorderLayout.WEST);
		
		SGPanel right = new SGPanel(new FlowLayout(FlowLayout.RIGHT,10,0));
		right.add(showAllLabel);
		add(right, BorderLayout.EAST);
		
	}
	
	/**
	 * 设置消息主体面板(ChannelMessageBodyPane)，因为主体面板和状态栏是关联的。
	 * 状态栏控制主体面板的展开(目前展开事件，是桌面切换到一个新的界面打开。)
	 * @param body
	 */
	public void setMessageBody(ChannelMessageBodyPane body)
	{
		this.body = body;
		this.body.addMessageChangeListener(this);
	}
	
	public ChannelMessageBodyPane getMessageBody()
	{
		return body;
	}
	
	/**
	 * 点击事件
	 */
	protected void doClick()
	{
		if(body != null) {
			body.showAllMessage();
		}
	}

	//这里通知来了一份新消息，同时更新计数器。
	@Override
	public void propertyChange(PropertyChangeEvent evt)
	{
		try {
			channelLock.lock();
			
			if (evt.getPropertyName() == EventConstants.MAIL_COUNT_CHANGE_PROPERTY)
			{
				int newCount = evt.getNewValue() instanceof Integer ? 
						((Integer)evt.getNewValue()).intValue(): 0;
						if(newCount != 0) {
							mailCount += newCount;
							mailLabel.setText("邮件：" + mailCount);
						}
				
			} else if (evt.getPropertyName() == EventConstants.QQ_COUNT_CHANGE_PROPERTY)
			{
				int newCount = evt.getNewValue() instanceof Integer ? 
						((Integer)evt.getNewValue()).intValue(): 0;
						if(newCount != 0) {
							qqCount += newCount;
							qqLabel.setText("QQ：" + qqCount);
						}

			} else if (evt.getPropertyName() == EventConstants.WEIBO_COUNT_CHANGE_PROPERTY)
			{
				int newCount = evt.getNewValue() instanceof Integer ? 
						((Integer)evt.getNewValue()).intValue(): 0;
						if(newCount != 0) {
							weiboCount += newCount;
							weiboLabel.setText("微博：" + weiboCount);
						}
			}
		}
		finally {
			channelLock.unlock();
		}
	}
}