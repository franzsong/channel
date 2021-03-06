package weibo4j.examples.timeline;

import weibo4j.Timeline;
import weibo4j.examples.oauth2.Log;
import weibo4j.model.Status;
import weibo4j.model.StatusWapper;
import weibo4j.model.WeiboException;

public class GetFriendsTimeline {

	public static void main(String[] args) {
//		String access_token = args[0];
		Timeline tm = new Timeline();
		tm.client.setToken("2.00RbcE5DRTD23Ce864c625f8_wxFwC");
		try {
			StatusWapper status = tm.getFriendsTimeline(0,0,10);
			for(Status s : status.getStatuses()){
				Log.logInfo(s.toString());
			}
			System.out.println(status.getNextCursor());
			System.out.println(status.getPreviousCursor());
			System.out.println(status.getTotalNumber());
			System.out.println(status.getHasvisible());
		} catch (WeiboException e) {
			e.printStackTrace();
		}

	}

}
