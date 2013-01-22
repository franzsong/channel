﻿package com.original.serive.channel.ui;

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.event.MouseInputListener;

import com.original.serive.channel.EventConstants;
import com.original.serive.channel.comp.CPanel;
import com.original.serive.channel.comp.CScrollBar;
import com.original.serive.channel.comp.CScrollPanel;
import com.original.serive.channel.layout.VerticalGridLayout;
import com.original.serive.channel.server.ChannelAccesser;
import com.original.serive.channel.util.ChannelConstants;
import com.original.serive.channel.util.ChannelUtil;
import com.original.serive.channel.util.GraphicsHandler;
import com.original.serive.channel.util.IconFactory;
import com.original.service.channel.ChannelMessage;
import com.original.service.channel.core.ChannelService;
import com.original.service.channel.core.MessageFilter;
import com.original.service.channel.core.MessageManager;
import com.original.service.channel.core.QueryItem;
import com.original.service.channel.event.MessageEvent;
import com.original.service.channel.event.MessageListner;

/**
 * Channel用户桌面，该桌面具有切换功能，有默认显示面板(即消息列表面板)，可以切换至其他面板
 * (使用{@link ChannelDesktopPane#addOtherShowComp()即可})。
 * 
 * @author WMS
 *
 */
public class ChannelDesktopPane extends CPanel implements MessageListner, AdjustmentListener, MouseInputListener, PropertyChangeListener, EventConstants
{
	private CardLayout layoutMgr = new CardLayout(); //卡片布局，带有切换功能
	public static Dimension SIZE = new Dimension(ChannelConstants.CHANNELWIDTH, 
			ChannelConstants.DESKTOPHEIGHT);
	public static ImageIcon BACKGROUND = IconFactory.loadIconByConfig("background"), //背景图片
			TOPICON = IconFactory.loadIconByConfig("top");//置顶图标
	
	private static final String DEFAULT_NAME = "DEFAULT",//默认面板的名称
			FILTER_NAME = "FILTER";//查找过滤面板的名称
	public static CPanel DEFAULT_PANE = new CPanel(),//桌面默认显示的面板，消息列表面板，带滚动条
			FILTER_PANE = null; //过滤面板，和默认面板一致
	public static CScrollBar DEFAULT_SCROLLBAR = 
			new CScrollBar(JScrollBar.VERTICAL, new Color(225,240,240)), //默认显示面板的滚动条
			FILTER_SCROLLBAR = 
			new CScrollBar(JScrollBar.VERTICAL, new Color(225,240,240)); //过滤面板的滚动条，个默认面板一致
	
	public static LayoutManager DEFAULT_DOWN_LAYOUT = //默认布局方式
			new VerticalGridLayout(VerticalGridLayout.BOTTOM_TO_TOP, 0, 8, new Insets(0, 0, 0, 0)), 
			DEFAULT_UP_LAYOUT = 
			new VerticalGridLayout(VerticalGridLayout.TOP_TO_BOTTOM, 0, 8, new Insets(0, 0, 0, 0));
	
	public static Lock channelLock = new ReentrantLock(); //用于控制消息的添加，即initMessage()和addMessage()的同步
	
	//记录上一次选中的类型和状态
	private String lastSelectedType = VIEW_ALL_TYPE, lastSelectedStatus = VIEW_ALL_STATUS;
	
	//显示更新信息和置顶图标的区域
	private Rectangle showMoreArea = null, topIconArea = null; 
	private String showMoreTip = "显示更多信息";
	
	//当前显示的面板
	private Component currentShowComp = DEFAULT_PANE;
	
	public ChannelDesktopPane() {
		setLayout(layoutMgr);
		DEFAULT_PANE.setLayout(DEFAULT_UP_LAYOUT);
		DEFAULT_SCROLLBAR.addAdjustmentListener(this);
		
		addDefaultShowComp(DEFAULT_NAME, DEFAULT_PANE);
	}
	
	/**
	 * 初始化桌面消息显示列表，注意和addMessage使用的布局方式不同，用途也不同
	 * @param msg
	 */
	public void initMessage(ChannelMessage msg) {
		initMessage(DEFAULT_PANE, msg);
	}
	
	/**
	 * 初始化桌面消息显示列表，注意和addMessage使用的布局方式不同，用途也不同
	 * @param owner 显示消息的面板
	 * @param msg
	 */
	public void initMessage(CPanel owner, ChannelMessage msg)
	{
		if(!checkMsgValidity(msg))
			return;
		
		boolean isLock = false;
		try {
			if(isLock = channelLock.tryLock()) {
				ChannelMessagePane msgContainer = null;

				if ((msgContainer = findMessage(owner, msg)) == null) {
					msgContainer = new ChannelMessagePane();
					msgContainer.initMessage(msg);
					
					owner.add(msgContainer);
					owner.validate();
				} else {
					msgContainer.initMessage(msg);
				}
			}
		} finally {
			if(isLock) {
				channelLock.unlock();
			}
		}
	}
	
	/**
	 * 添加消息，这里是消息添加的入口，使用时最好由线程调用。
	 * @param msg
	 */
	public void addMessage(ChannelMessage msg) {
		if (DEFAULT_PANE != currentShowComp) {
			showDefaultComp();
		}
		addMessage(DEFAULT_PANE, msg);
	}
	
	/**
	 * 添加消息，这里是消息添加的入口，使用时最好由线程调用。
	 * @param msg
	 * @param owner 添加消息的面板
	 */
	public void addMessage(CPanel owner, ChannelMessage msg)
	{
		if (!checkMsgValidity(msg))
			return;

		boolean isLock = false;
		try {
			if(isLock = channelLock.tryLock()) {
				ChannelMessagePane msgContainer = null;
				if ((msgContainer = findMessage(owner, msg)) == null) {
					msgContainer = new ChannelMessagePane();
				}
				msgContainer.addMessage(msg, true);
				owner.add(msgContainer, 0);
				owner.validate();

				//如果当前显示界面已经切换到<显示全部>面板，则该面板也要添加最新消息
				CPanel showComp = (CPanel) currentShowComp();
				if (showComp != DEFAULT_PANE && showComp != FILTER_PANE
						&& showComp instanceof ChannelMessagePane) {
					msgContainer = (ChannelMessagePane) showComp;
					msgContainer.addMessage(msg, false);
				}
			}
		}
		finally {
			if(isLock) {
				channelLock.unlock();
			}
		}
	}
	
	/**
	 * 查找消息面板。由于消息面板是和联系人Uid绑定，所以添加消息时，需要事先查找一下该Uid有没有消息面板。
	 * 有则使用该面板添加消息。
	 * @param msg 消息对象
	 * @return
	 */
	public ChannelMessagePane findMessage(CPanel owner, ChannelMessage msg)
	{
		if(!checkMsgValidity(msg))
			return null;
		
		for(int i=0; i<owner.getComponentCount(); i++)
		{
			Component comp = owner.getComponent(i);
			if(comp instanceof ChannelMessagePane)
			{
				String uid = ((ChannelMessagePane) comp).uid;
				String uName = msg.getContactName();
				if(uid != null && uid.equals(uName))
					return (ChannelMessagePane)comp;
			}
		}
		return null;
	}
	
	/**
	 * 这里对消息的有效性进行检查，以后其他面板，如{@link ChannelMessagePane}添加消息时，将不再做检查。
	 * 
	 * @param msg
	 *            消息报文
	 * @return
	 */
	public boolean checkMsgValidity(ChannelMessage msg) {
		// 这里目前只检查是否为空，以及标识Id是否为空
		if (msg == null || msg.getMessageID() == null) {
			return false;
		}
		// throw new
		// IllegalArgumentException("Invalid Message! Cause by it or it's ID is null.");

		return true;
	}
	
	/**
	 * 获取当前显示的面板，如果当前面板是滚动面板，则获取其子面板(DEFAULT_PANE除外)
	 * @return
	 */
	public Component currentShowComp() {
		Component comp = null;
		for (int i = 0; i < getComponentCount(); i++) {
			comp = getComponent(i);
			if (comp.isVisible()) {
				if (comp instanceof CScrollPanel) {
					comp = ((CScrollPanel) comp).getViewport().getView();
					
					if(comp != DEFAULT_PANE && comp != FILTER_PANE) {//other pane. @see addOtherShowComp()
						comp = ((JComponent)comp).getComponent(0);
					}
				}
				break;
			}
		}
		return comp;
	}
	
	/**
	 * 添加默认显示的面板。目前主要添加DEFAULT_PANE和FILTER_PANE
	 * @param comp
	 */
	private void addDefaultShowComp(String name, JComponent comp)
	{
		comp.setOpaque(false);
		CScrollPanel jsp = new CScrollPanel(comp,
				CScrollPanel.VERTICAL_SCROLLBAR_AS_NEEDED,
				CScrollPanel.HORIZONTAL_SCROLLBAR_NEVER);

		if (comp == DEFAULT_PANE) {
			jsp.setVerticalScrollBar(DEFAULT_SCROLLBAR);
		} else if (comp == FILTER_PANE) {
			jsp.setVerticalScrollBar(FILTER_SCROLLBAR);
		}
		jsp.setBorder(BorderFactory.createEmptyBorder(25, 25, 55, 5));
		
		//设置滚动面板透明
		jsp.setViewportBorder(null);
		jsp.setOpaque(false);
		jsp.getViewport().setOpaque(false);
		
		jsp.setName(name);
		jsp.addMouseListener(this);
		jsp.addMouseMotionListener(this);
		add(name, jsp);
		
		//记录当前显示的组件和上一次显示的组件
		this.putClientProperty(LAST_SHOW_COMPONENT, null);
		this.putClientProperty(CURRENT_SHOW_COMPONENT, name);
		
		jsp.putClientProperty(LAST_SHOW_COMPONENT, this.getClientProperty(LAST_SHOW_COMPONENT));
	}
	
	/**
	 * 添加其他面板，并同时显示该面板
	 * @param name 面板名称，唯一
	 * @param comp
	 */
	public void addOtherShowComp(String name, JComponent comp)
	{
		if(name == null)
			return;
		
		//如果已有该面板，则显示
		if(indexOfShowComp(name) != -1) {
			showComp(name);
			return;
		}
		
		JPanel otherPane = new JPanel(DEFAULT_DOWN_LAYOUT); 
		otherPane.add(comp);
		otherPane.setOpaque(false);
		//为载体添加滚动条
		JScrollPane jsp =  ChannelUtil.createScrollPane(otherPane, new Color(225,240,240));
		jsp.setBorder(BorderFactory.createEmptyBorder(25, 25, 55, 5));
		jsp.setName(name);
		
		add(name, jsp);
		showComp(name);
		
		//记录当前显示的组件和上一次显示的组件
		this.putClientProperty(LAST_SHOW_COMPONENT, this.getClientProperty(CURRENT_SHOW_COMPONENT));
		this.putClientProperty(CURRENT_SHOW_COMPONENT, name);
		
		jsp.putClientProperty(LAST_SHOW_COMPONENT, this.getClientProperty(LAST_SHOW_COMPONENT));
	}
	
	/**
	 * 显示默认面板
	 */
	public void showDefaultComp()
	{
		showComp(DEFAULT_NAME);
	}
	
	/**
	 * 显示其他面板
	 * @param name 面板名称
	 */
	public void showComp(String name)
	{
		if (name == null) {
			name = DEFAULT_NAME;
		}
		
		if (!DEFAULT_NAME.equals(name) && !FILTER_NAME.equals(name)) {
			setScrollBarVisible(null, false);// 其他面板不显示滚动条
		} else {
			CScrollBar scrollBar = DEFAULT_NAME.equals(name) ? DEFAULT_SCROLLBAR : FILTER_SCROLLBAR;
			
			Boolean showStatus = (Boolean) scrollBar.getClientProperty(SCROLLBAR_SHOW_STATUS);
			setScrollBarVisible(scrollBar, showStatus == null ? false : showStatus.booleanValue());
		}
		
		layoutMgr.show(this, name);
		
		//记录当前显示的组件和上一次显示的组件
//		CScrollPanel scrollParentComp = getScrollParentComp(currentShowComp);
//		this.putClientProperty(LAST_SHOW_COMPONENT, scrollParentComp.getName());
//		this.putClientProperty(CURRENT_SHOW_COMPONENT, name);
		
		//设置当前显示面板
		currentShowComp = currentShowComp();
		if(currentShowComp == null) {
			currentShowComp = DEFAULT_PANE;
		}
	}
	
	/**
	 * 获取每个面板的滚动父面板
	 * @param comp
	 * @return
	 */
	private CScrollPanel getScrollParentComp(Component comp)
	{
		if(comp instanceof CScrollPanel)
			return (CScrollPanel)comp;
		
		Component parent = comp.getParent();
		while (!(parent instanceof CScrollPanel)) {
			parent = parent.getParent();
		}
		return (CScrollPanel) parent;
	}
	
	/**
	 * 获取默认的过滤面板
	 * @return
	 */
	private CPanel getDefaultFilter() {
		if (FILTER_PANE == null) {
			FILTER_PANE = new CPanel();
			FILTER_PANE.setLayout(DEFAULT_UP_LAYOUT);
			FILTER_SCROLLBAR.addAdjustmentListener(this);
			addDefaultShowComp(FILTER_NAME, FILTER_PANE);
		} else { // 每次打开时，都清空面板，以便重新加载数据
			FILTER_PANE.removeAll();
			FILTER_PANE.validate();
			this.validate();
			this.repaint();
		}
		return FILTER_PANE;
	}
	
	/**
	 * 是否已包含name对应的面板
	 * @param name 面板名称
	 * @return
	 */
	public int indexOfShowComp(String name)
	{
		if(name != null) {
			for(int i=0; i<getComponentCount();i++) {
				if(name.equals(getComponent(i).getName()))
					return i;
			}
		}
		return -1;
	}
	
	/**
	 * 移除其他面板，同时返回历史面板
	 * @param name 面板名称
	 */
	public void removeShowComp(String name)
	{
		int index = -1;
		if(name == null || DEFAULT_NAME.equals(name) || FILTER_NAME.equals(name)
				|| (index = indexOfShowComp(name)) == -1)
			return;
		
		remove(index);
		//返回历史面板
		String history = (String)this.getClientProperty(LAST_SHOW_COMPONENT);
		showComp(history);
		
		//更新当前显示的组件和上一次显示的组件
		index = indexOfShowComp(history);
		JComponent lastShowComp = index == -1 ? null : (JComponent)getComponent(index);
		
		this.putClientProperty(CURRENT_SHOW_COMPONENT, history);
		this.putClientProperty(LAST_SHOW_COMPONENT, lastShowComp == null ? null :
			lastShowComp.getClientProperty(LAST_SHOW_COMPONENT));
	}

	@Override
	public void paintComponent(Graphics g)
	{
		Graphics2D g2d = GraphicsHandler.optimizeGraphics(g);
//		
//		Area bounds = new Area(new Rectangle(0, 0, getWidth(), getHeight()));
//		Color backColor = new Color(35,85,105);
//		g2d.setColor(backColor);
//		
//		int radius = (int)(getWidth()/2);
//		Point2D center = new Point(getWidth()/2, getHeight()/2);
//		RadialGradientPaint paint = new RadialGradientPaint(center, radius,  
//                new float[]{0.0f, 1.0f}, new Color[]{backColor.brighter().brighter(), backColor});  
//		g2d.setPaint(paint);
//		g2d.fill(bounds);
//		GraphicsHandler.suspendRendering(g2d);
		g2d.drawImage(BACKGROUND.getImage(), 0, 0, this);
		
		boolean isScrollBarVisible = currentShowComp == DEFAULT_PANE ? DEFAULT_SCROLLBAR.isScrollBarVisible()
				: (currentShowComp == FILTER_PANE ? FILTER_SCROLLBAR.isScrollBarVisible() : false);
		
		if(isScrollBarVisible) {
			g2d.setFont(ChannelConstants.DEFAULT_FONT.deriveFont(16F));
			g2d.setColor(Color.WHITE);
			
			if(showMoreArea == null) {
				int fontWidth = g2d.getFontMetrics().stringWidth(showMoreTip), 
						fontHeight = g2d.getFont().getSize();
				showMoreArea = new Rectangle((SIZE.width - fontWidth) / 2,
						SIZE.height - fontHeight - 5, 
						fontWidth, 
						fontHeight);
			}
			g2d.drawString(showMoreTip, showMoreArea.x, showMoreArea.y);
			
			if (topIconArea == null) {
				topIconArea = new Rectangle(SIZE.width - TOPICON.getIconWidth() - 25, 
						SIZE.height - TOPICON.getIconHeight() - 15,
						TOPICON.getIconWidth(), 
						TOPICON.getIconHeight());
			}
			g2d.drawImage(TOPICON.getImage(), topIconArea.x, topIconArea.y, this);
		}
	}

	@Override
	public void adjustmentValueChanged(AdjustmentEvent e) {
		// TODO 自动生成的方法存根
		if(e.getAdjustmentType() == AdjustmentEvent.TRACK) {
			CScrollBar scrollBar =(CScrollBar) e.getSource();
			
			//滚动条消失
			if (scrollBar.isScrollBarVisible() && scrollBar.getValue() == 0
					&& !scrollBar.isVisible()/*scrollBarAmount == 0*/) {
				setScrollBarVisible(scrollBar, false);
				scrollBar.putClientProperty(SCROLLBAR_SHOW_STATUS, Boolean.FALSE); // 记录滚动条的状态
			}
			
			if(scrollBar.getValue() == scrollBar.getScrollBarValue() &&
					scrollBar.getVisibleAmount() == scrollBar.getScrollBarAmount())
				return; //滚动条未发生变化
			
			scrollBar.setScrollBarValue(scrollBar.getValue());
			scrollBar.setScrollBarAmount (scrollBar.getVisibleAmount());
			
			//滚动条移至底部(不考虑滚动条最大的情况，即scrollBar.getMaximum() == scrollBarAmount的情况)
			if (scrollBar.getScrollBarValue() != 0
					&& scrollBar.getScrollBarValue() == (scrollBar.getMaximum() - scrollBar.getScrollBarAmount())) {
				if (!scrollBar.isScrollBarVisible()) {
//					scrollBar.setScrollBarVisible(true);
					setScrollBarVisible(scrollBar, true);
					scrollBar.putClientProperty(SCROLLBAR_SHOW_STATUS, Boolean.TRUE); // 记录滚动条的状态
				}

				// add Message
				
			}
		}
	}
	
	/**
	 * 设置滚动条是否可见
	 * @param isVisible 如果为true，则可见；否则不可见
	 */
	public void setScrollBarVisible( CScrollBar scrollBar, boolean isVisible) {
		if (scrollBar != null && scrollBar.isScrollBarVisible() != isVisible) {
			scrollBar.setScrollBarVisible(isVisible);
			repaint();
		} else if (scrollBar == null) {
			repaint();
		}
	}
	
	/**
	 * 显示更多的消息
	 */
	private void showMoreMessage() {
		System.out.println("show more message!");
	}

	/**
	 * 滚动条置顶
	 */
	private void backToTop() {
		DEFAULT_SCROLLBAR.setValue(0);
	}
	
	/**
	 * 构建查询项
	 * @return
	 */
	private QueryItem createQueryItem() {
		QueryItem qi = new QueryItem();
		
		if (lastSelectedType != VIEW_ALL_TYPE) {
			qi.setFilters(new MessageFilter("clazz", 
					lastSelectedType == VIEW_WEIBO ? ChannelMessage.WEIBO : 
						 (lastSelectedType == VIEW_QQ ? ChannelMessage.QQ : ChannelMessage.MAIL)
			));
		}
		
		if(lastSelectedStatus != VIEW_ALL_STATUS) {
			if (lastSelectedStatus == VIEW_DRAFT) {// 草稿箱
				qi.setKeys(ChannelMessage.FLAG_DRAFT);
				qi.setValues(1);
			} else if (lastSelectedStatus == VIEW_TRASH) {// 垃圾箱
				qi.setKeys(ChannelMessage.FLAG_TRASHED);
				qi.setValues(1);
			} else if (lastSelectedStatus == VIEW_UNDO) {// 未处理，比较特殊(即未读、且不是草稿或垃圾)。待优化！
				qi.setKeys(ChannelMessage.FLAG_SEEN, ChannelMessage.FLAG_DRAFT, ChannelMessage.FLAG_TRASHED);
				qi.setValues(0, 0, 0);
			}
		}
		
		return qi;
	}
	
	@Override
	public void mouseClicked(MouseEvent e) {
		// TODO 自动生成的方法存根
		Point point  = e.getPoint();
		if (showMoreArea != null
				&& showMoreArea.contains(point.x, point.y + showMoreArea.height)) {
			showMoreMessage();
		} else if (topIconArea != null && topIconArea.contains(point)) {
			backToTop();
		} 
	}
	
	@Override
	public void mouseMoved(MouseEvent e) {
		// TODO 自动生成的方法存根
		Point point  = e.getPoint();
		if (showMoreArea != null
				&& showMoreArea.contains(point.x, point.y + showMoreArea.height)) {
			setCursor(ChannelConstants.HAND_CURSOR);
		} else if (topIconArea != null && topIconArea.contains(point)) {
			setCursor(ChannelConstants.HAND_CURSOR);
		} else {
			setCursor(ChannelConstants.DEFAULT_CURSOR);
		}
	}

	@Override
	public void mousePressed(MouseEvent e) { }

	@Override
	public void mouseReleased(MouseEvent e) { }

	@Override
	public void mouseEntered(MouseEvent e) { }

	@Override
	public void mouseExited(MouseEvent e) { }

	@Override
	public void mouseDragged(MouseEvent e) { }
	
//查找和过滤	
	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if(evt.getPropertyName() != STATUS_CHANGE_PROPERTY
				&& evt.getPropertyName() != TYPE_CHANGE_PROPERTY 
				&& evt.getPropertyName() != SEARCHTEXT_CHANGE_PROPERTY) {
			return;
		}
		
		//搜索
		QueryItem qi  = null;
		if(evt.getPropertyName() == SEARCHTEXT_CHANGE_PROPERTY) {
			qi  = createQueryItem(); //搜索的查询项
			qi.setText((String)evt.getNewValue());
		}
		
		//过滤
		if (evt.getPropertyName() == STATUS_CHANGE_PROPERTY) {
			lastSelectedStatus = (String) evt.getNewValue();
		} else if (evt.getPropertyName() == TYPE_CHANGE_PROPERTY) {
			lastSelectedType = (String) evt.getNewValue();
		}
		
		if(qi == null
				&& lastSelectedStatus == VIEW_ALL_STATUS
				&& lastSelectedType == VIEW_ALL_TYPE) {
			showDefaultComp();
			return;
		}
		
		if(qi == null) qi = createQueryItem(); //过滤的查询项
		
		ChannelService cs = ChannelAccesser.getChannelService();
		MessageManager mm = cs.getMsgManager();
		List<ChannelMessage> filterMsgs = mm.getMessagesByAll(qi);
		
		//获取过滤面板
		CPanel filterPane = getDefaultFilter();
		if (!filterMsgs.isEmpty()) {
			for (ChannelMessage msg : filterMsgs) {
				initMessage(filterPane, msg);
			}
		} else { //刷新一下desktop
			this.validate();
			this.repaint();
		}
		
		showComp(FILTER_NAME);
	}

	@Override
	public void change(MessageEvent evnt)
	{
		if (evnt.getType() == MessageEvent.Type_Added) {
			addMessage(evnt.getAdded()[0]);
		}
	}
}
