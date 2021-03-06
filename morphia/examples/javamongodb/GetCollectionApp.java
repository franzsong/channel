package javamongodb;
 
import java.net.UnknownHostException;
import java.util.Set;
 
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.Mongo;
import com.mongodb.MongoException;
 
/**
 * Java : Get collection from MongoDB
 * 
 */
public class GetCollectionApp {
	public static void main(String[] args) {
 
		try {
 
			Mongo mongo = new Mongo("localhost", 27017);
			DB db = mongo.getDB("song");
 
			// get list of collections
			Set<String> collections = db.getCollectionNames();
 
			for (String collectionName : collections) {
				System.out.println(collectionName);
			}
			System.out.println("-----------------");
			// get a single collection
			DBCollection collection = db.getCollection("users");
			System.out.println(collection.toString());
 
			System.out.println("Done");
 
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (MongoException e) {
			e.printStackTrace();
		}
 
	}
}