package weibo4j.examples.comment;

import weibo4j.Comments;
import weibo4j.examples.oauth2.Log;
import weibo4j.model.Comment;
import weibo4j.model.CommentWapper;
import weibo4j.model.WeiboException;

public class GetCommentToMe {

	public static void main(String[] args) {
//		String access_token = args[0];
		Comments cm = new Comments();
		cm.client.setToken("2.00RbcE5DRTD23Ce864c625f8_wxFwC");
		try {
			CommentWapper comment = cm.getCommentToMe();
			for(Comment c :comment.getComments()){
				Log.logInfo(c.toString());
			}
			System.out.println(comment.getTotalNumber());
			System.out.println(comment.getNextCursor());
			System.out.println(comment.getNextCursor());
			System.out.println(comment.getHasvisible());
		} catch (WeiboException e) {
			e.printStackTrace();
		}
	}

}
