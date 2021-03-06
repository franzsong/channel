/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package iqq.service;

import iqq.comm.Auth;
import iqq.comm.Auth.AuthInfo;
import iqq.model.Category;
import iqq.model.Member;
import iqq.ui.MainFrame;
import iqq.ui.MainPanel;
import iqq.util.AePlayWave;
import iqq.util.Log;
import iqq.util.Method;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import atg.taglib.json.util.JSONArray;
import atg.taglib.json.util.JSONException;
import atg.taglib.json.util.JSONObject;

/**
 *
 * @author chenzhihui
 */
public class CategoryService {
	private static CategoryService categoryService = null;

    /**
     * @param aRecentList the recentList to set
     */
    private MemberService memberService = MemberService.getInstance();
    private HttpService httpService = null;
    
    private static Map<String, List<Category>> categoryMap = new HashMap<String, List<Category>>();
    //onlineList、recentList目前没有对多用户进行处理，只有categoryMap支持多用户，以后再优化！！！
    private static List<Member> onlineList = null;
    private static List<Member> recentList = null;

    private CategoryService() {
    }

    public static CategoryService getInstance() {
        if (categoryService == null) {
            categoryService = new CategoryService();
        }
        return categoryService;
    }

    /**
     * 获取默认分组(即“我的好友”)，并设置其对应的好友列表
     * @param friends 好友列表
     * @param info 好友信息列表
     * @param marknames 好友备注信息列表
     * @return
     * @throws Exception
     */
    private synchronized Category getDefaultCategory(JSONArray friends, JSONArray info, JSONArray marknames) throws Exception {
    	Category cDef = new Category();
    	cDef.setIndex(0);
    	cDef.setSort(0);
    	cDef.setName("我的好友");
    	
    	if(friends != null && friends.length() > 0)
    	{
    		Member m = null;
    		for(int i=0; i<friends.size(); i++)
    		{
    			JSONObject friend = friends.getJSONObject(i);
    			if(friend.getInt("categories") == cDef.getIndex()) 
    			{
    				m = new Member();
    				m.setUin(friend.getLong("uin"));
                    m.setFlag(friend.getInt("flag"));
                    m.setCategory(cDef);
                    m.setNickname(info.getJSONObject(i).getString("nick"));
//                    for (int k = 0; k < info.size(); k++) {
//                        if (m.getUin() == info.getJSONObject(k).getLong("uin")) {
//                            m.setNickname(info.getJSONObject(k).getString("nick"));
//                            break;
//                        }
//                    }
                    for (int k = 0; k < marknames.size(); k++) {
                        if (m.getUin() == marknames.getJSONObject(k).getLong("uin")) {
                            m.setMarkname(marknames.getJSONObject(k).getString("markname"));
                            break;
                        }
                    }
                    
                    cDef.getMemberList().add(m);
    			}
    		}
    	}
    	
    	return cDef;
    }
    
    public synchronized List<Category> getFriends() throws Exception {
    	return getFriends(null);
    }
    public synchronized List<Category> getFriends(AuthInfo ai) throws Exception {
    	if(ai == null) ai = Auth.getSingleAccountInfo();
    	List<Category> categoryList = categoryMap.get(ai.getMember().getAccount());
    	if(categoryList != null)
    		return categoryList;
    	
        //所有好友
        String urlStr = "http://s.web2.qq.com/api/get_user_friends2";
        String contents = "{\"h\":\"hello\",\"vfwebqq\":\"" + ai.getVfwebqq() + "\"}";
        contents = URLEncoder.encode(contents, "UTF-8");
        contents = "r=" + contents;
        
        httpService = new HttpService(urlStr, Method.POST);
        httpService.setContents(contents);
        String result = httpService.sendHttpMessage();

        JSONObject retJson = new JSONObject(result);
//        JSONObject statusRetJson = JSONObject.fromObject(statusResult);
        if (retJson.getInt("retcode") == 0) {
            JSONObject obj = retJson.getJSONObject("result");
            JSONArray categories = obj.getJSONArray("categories");
            JSONArray friends = obj.getJSONArray("friends");
            JSONArray info = obj.getJSONArray("info");
            JSONArray marknames = obj.getJSONArray("marknames");
            JSONArray vipinfo = obj.getJSONArray("vipinfo");
            Category c = null;
            Member m = null;
            Category cDef = getDefaultCategory(friends, info, marknames); //默认分组(即“我的好友”)
            
            categoryList = new ArrayList<Category>();
            for (int i = 0; i < categories.size(); i++) {
                c = new Category();
                c.setIndex(categories.getJSONObject(i).getInt("index"));
                c.setSort(categories.getJSONObject(i).getInt("sort"));
                c.setName(categories.getJSONObject(i).getString("name"));
                for (int j = 0; j < friends.size(); j++) {
                    int index = friends.getJSONObject(j).getInt("categories");
                    if (index == c.getIndex()) {
                        m = new Member();
                        m.setUin(friends.getJSONObject(j).getLong("uin"));
                        m.setFlag(friends.getJSONObject(j).getInt("flag"));
                        m.setCategory(c);
                        m.setNickname(info.getJSONObject(j).getString("nick"));
//                        for (int k = 0; k < info.size(); k++) {
//                            if (m.getUin() == info.getJSONObject(k).getLong("uin")) {
//                                m.setNickname(info.getJSONObject(k).getString("nick"));
//                                break;
//                            }
//                        }
                        for (int k = 0; k < marknames.size(); k++) {
                            if (m.getUin() == marknames.getJSONObject(k).getLong("uin")) {
                                m.setMarkname(marknames.getJSONObject(k).getString("markname"));
                                break;
                            }
                        }

                        c.getMemberList().add(m);
                    }
                }
                if (categoryList.isEmpty()) {
                    categoryList.add(c);
                } else {
                    boolean isExist = false;
                    for (Category cate : categoryList) {
                        if (c.getIndex() == cate.getIndex()) {
                            isExist = true;
                        }
                    }
                    if (!isExist) {
                        categoryList.add(c);
                    }
                }
            }
            if (cDef != null) {
                categoryList.add(0, cDef);
            }
            categoryMap.put(ai.getMember().getAccount(), categoryList);
        }
        return categoryList;
    }

    public synchronized List<Member> getOnlineFriends(AuthInfo ai) throws Exception {
        if (onlineList != null) {
            return onlineList;
        } else {
            onlineList = new ArrayList<Member>();
        }
        String urlStr = "http://d.web2.qq.com/channel/get_online_buddies2?clientid=" + ai.getClientid() + "&psessionid=" + ai.getPsessionid() + "&t=" + System.currentTimeMillis();

        httpService = new HttpService(urlStr, Method.GET);
        String result = httpService.sendHttpMessage();

        JSONObject retJson = new JSONObject(result);
        JSONArray statusArray = null;
        if (retJson.getInt("retcode") == 0) {
            statusArray = retJson.getJSONArray("result");
            List<Category> categoryList = categoryMap.get(ai.getMember().getAccount());
            
            if(categoryList != null)
            for (int i = 0; i < categoryList.size(); i++) {
                for (int k = 0; k < categoryList.get(i).getMemberList().size(); k++) {
                    Member m = categoryList.get(i).getMemberList().get(k);
                    for (int s = 0; s < statusArray.size(); s++) {
                        if (m.getUin() == statusArray.getJSONObject(s).getLong("uin")) {
                            m.setStatus(statusArray.getJSONObject(s).getString("status"));
                            if (!categoryList.get(i).getMemberList().isEmpty() && categoryList.get(i).getMemberList().get(0).getUin() != m.getUin()) {
                                categoryList.get(i).getMemberList().remove(k);
                                categoryList.get(i).getMemberList().add(0, m);
                                categoryList.get(i).setOnlineCount(categoryList.get(i).getOnlineCount() + 1);
                            }
                            onlineList.add(m);
                        }
                    }
                }
            }
        }
        Log.println("online" + onlineList.size());
        return onlineList;
    }

    public synchronized List<Member> getRecentList(AuthInfo ai) {
        try {
            String urlStr = "http://d.web2.qq.com/channel/get_recent_list2";
            String contents = "{\"vfwebqq\":\"" + ai.getVfwebqq() + "\",\"clientid\":\"" + ai.getClientid() + "\",\"psessionid\":\"" + ai.getPsessionid() + "\"}";
            contents = URLEncoder.encode(contents, "UTF-8");
            contents = "r=" + contents;

            httpService = new HttpService(urlStr, Method.POST, contents);
            String result = httpService.sendHttpMessage();
            JSONObject retJson = new JSONObject(result);
            JSONArray array = null;
            if (retJson.getInt("retcode") == 0) {
                if (recentList == null) {
                    recentList = new ArrayList<Member>();
                }

                array = retJson.getJSONArray("result");
                for (int i = 0; i < array.length(); i++) {
                    long uin = array.getJSONObject(i).getLong("uin");
                    List<Category> categoryList = categoryMap.get(ai.getMember().getAccount());
                    
                    if(categoryList != null)
                    for (Category c : categoryList) {
                        List<Member> memberList = c.getMemberList();
                        for (Member m : memberList) {
                            if (uin == m.getUin()) {
                                if (!recentList.contains(m)) {
                                    recentList.add(m);
                                }
                            }
                        }
                    }
                }
            }

        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(CategoryService.class.getName()).log(Level.SEVERE, null, ex);
        } catch (JSONException ex) {
            Logger.getLogger(CategoryService.class.getName()).log(Level.SEVERE, null, ex);
        }
        Log.println("recentList Size:" + recentList);
        return recentList;
    }

    public synchronized void changeStatus(AuthInfo ai, Member member) throws Exception {
    	  List<Category> categoryList = categoryMap.get(ai.getMember().getAccount());
    	  if(categoryList == null) return;
    	
        for (Category c : categoryList) {
            List<Member> memberList = c.getMemberList();
            for (int i = 0; i < memberList.size(); i++) {
                Member m = memberList.get(i);
                if (m.getUin() == member.getUin()) {
                    m.setStatus(member.getStatus());

                    int count = m.getCategory().getOnlineCount();
                    if (m.getStatus() == null || m.getStatus().equals("") || m.getStatus().equals("hidden")
                            || m.getStatus().equals("offline")) {
                        if (count > 0) {
                            --count;
                        } else {
                            count = 0;
                        }
                        m.getCategory().setOnlineCount(count);
                        memberList.remove(i);
                        memberList.add(memberList.size(), m);
                    } else {
                        m.getCategory().setOnlineCount(++count);
                        memberList.remove(i);
                        memberList.add(0, m);
                    }
                }
            }
        }

        boolean newOnline = true;
        for (int i = 0; i < onlineList.size(); i++) {
            if (onlineList.get(i).getUin() == member.getUin()) {
                onlineList.get(i).setStatus(member.getStatus());
                if (member.getStatus() == null || member.getStatus().equals("") || member.getStatus().equals("hidden")
                        || member.getStatus().equals("offline")) {
                    onlineList.remove(i);
                }
                newOnline = false;
            }
        }
        if (newOnline) {
            onlineList.add(member);
            //声音提示
            AePlayWave.play(AePlayWave.AUDIO_ONLINE);
        }

        member.setFace(memberService.downloadFace(ai, member));
        MainPanel mainPanel = (MainPanel) MainFrame.getMainFrame().getMap().get("mainPanel");

        mainPanel.changeStatus(member.getCategory());
    }

    public static List<Category> getCategoryList(AuthInfo ai) {
		if (ai == null || ai.getMember() == null)
			return null;
		return categoryMap.get(ai.getMember().getAccount());
    }

    public static void setCategoryList(AuthInfo ai, List<Category> categoryList) {
		if (ai == null || ai.getMember() == null)
			return;
		categoryMap.put(ai.getMember().getAccount(), categoryList);
    }

    public static List<Member> getOnlineList() {
        return onlineList;
    }

    public static void setOnlineList(List<Member> onlineList) {
        CategoryService.onlineList = onlineList;
    }

    /**
     * @param aRecentList the recentList to set
     */
    public static void setRecentList(List<Member> aRecentList) {
        recentList = aRecentList;
    }
}
