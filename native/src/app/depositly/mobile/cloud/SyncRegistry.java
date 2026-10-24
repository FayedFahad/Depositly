package app.depositly.mobile.cloud;

import java.util.*;
import org.json.*;

/** Same field registers and tombstones as assets/sync-registry.js; no financial arithmetic. */
public final class SyncRegistry {
    private SyncRegistry() {}
    private static final String[][] GROUPS={{"type","cycle","cycleUnit","start","first","maturity","autoRenew","renewalStop","tenureCount","tenureUnit"},{"ownership","ownerName"}};
    private static String escape(String value){return value.replace("~","~0").replace("/","~1");}
    private static String unescape(String value){return value.replace("~1","/").replace("~0","~");}
    private static List<String> keys(JSONObject value){List<String> keys=new ArrayList<>();Iterator<String> iterator=value.keys();while(iterator.hasNext())keys.add(iterator.next());Collections.sort(keys);return keys;}
    public static String canonical(Object value)throws Exception{
        if(value instanceof JSONObject){JSONObject object=(JSONObject)value;List<String> items=new ArrayList<>();for(String key:keys(object))items.add(JSONObject.quote(key)+":"+canonical(object.get(key)));return "{"+join(items)+"}";}
        if(value instanceof JSONArray){JSONArray array=(JSONArray)value;List<String> items=new ArrayList<>();for(int i=0;i<array.length();i++)items.add(canonical(array.get(i)));return "["+join(items)+"]";}
        if(value instanceof String)return JSONObject.quote((String)value);
        if(value instanceof Number)return JSONObject.numberToString((Number)value);
        return value==null||value==JSONObject.NULL?"null":value.toString();
    }
    private static String join(List<String> items){StringBuilder result=new StringBuilder();for(String item:items){if(result.length()>0)result.append(',');result.append(item);}return result.toString();}
    public static boolean equal(Object a,Object b)throws Exception{return canonical(a).equals(canonical(b));}
    private static int compare(JSONArray a,JSONArray b)throws Exception{int order=Long.compare(a.getLong(0),b.getLong(0));if(order==0)order=Long.compare(a.getLong(1),b.getLong(1));return order!=0?order:a.getString(2).compareTo(b.getString(2));}
    public static JSONObject empty()throws Exception{return new JSONObject().put("version",1).put("entries",new JSONObject());}
    public static JSONObject validate(JSONObject doc)throws Exception{
        if(!(doc.opt("version") instanceof Number)||doc.optDouble("version")!=1||doc.optJSONObject("entries")==null||doc.getJSONObject("entries").length()>500000)throw new Exception("Unsupported registry data.");
        JSONObject entries=doc.getJSONObject("entries");
        for(String key:keys(entries)){
            String[] path=key.split("/",-1);boolean entity=(path[0].equals("a")||path[0].equals("r"))&&(path.length==2||path.length==3);
            if(!entity&&!((path[0].equals("p")||path[0].equals("n"))&&path.length==2))throw new Exception("Invalid registry field.");
            for(int i=1;i<path.length;i++)if(path[i].isEmpty()||path[i].length()>2048)throw new Exception("Invalid registry key.");
            JSONObject record=entries.getJSONObject(key);JSONArray stamp=record.getJSONArray("s");
            if(stamp.length()!=3||!safeInteger(stamp.get(0))||!safeInteger(stamp.get(1))||!(stamp.get(2) instanceof String)||stamp.getString(2).isEmpty()||stamp.getString(2).length()>128||(record.has("d")&&!Boolean.TRUE.equals(record.get("d")))||(!record.optBoolean("d")&&!record.has("v")))throw new Exception("Invalid registry revision.");
        }
        return doc;
    }
    private static boolean safeInteger(Object value){if(!(value instanceof Number))return false;double n=((Number)value).doubleValue();return n>=0&&n<=9007199254740991d&&n==Math.floor(n);}
    private static JSONObject flatten(JSONObject backup)throws Exception{
        JSONObject data=backup.getJSONObject("data"),flat=new JSONObject();
        for(String key:keys(data))if(!Arrays.asList("assets","paid","exportedAt").contains(key))flat.put("p/"+escape(key),data.get(key));
        JSONObject reminders=backup.optJSONObject("notificationSettings");if(reminders==null)reminders=new JSONObject().put("enabled",false).put("time","09:00");
        for(String key:keys(reminders))flat.put("n/"+escape(key),reminders.get(key));
        JSONArray assets=data.getJSONArray("assets");
        for(int i=0;i<assets.length();i++){JSONObject asset=assets.getJSONObject(i);String base="a/"+escape(asset.getString("id"));flat.put(base,true);for(String field:keys(asset))flat.put(base+"/"+escape(field),asset.get(field));}
        JSONObject paid=data.getJSONObject("paid");for(String id:keys(paid)){JSONObject receipt=paid.getJSONObject(id);String base="r/"+escape(id);flat.put(base,true);for(String field:keys(receipt))flat.put(base+"/"+escape(field),receipt.get(field));}
        return flat;
    }
    public static JSONObject create(JSONObject backup,String actor,long time)throws Exception{
        JSONObject result=empty(),flat=flatten(backup),entries=result.getJSONObject("entries");
        for(String key:keys(flat))entries.put(key,new JSONObject().put("s",new JSONArray().put(Math.max(0,time)).put(0).put(actor)).put("v",flat.get(key)));
        JSONArray assets=backup.getJSONObject("data").getJSONArray("assets");for(int i=0;i<assets.length();i++)for(String[] group:GROUPS)for(String field:group){String key="a/"+escape(assets.getJSONObject(i).getString("id"))+"/"+field;if(!entries.has(key))entries.put(key,new JSONObject().put("s",new JSONArray().put(Math.max(0,time)).put(0).put(actor)).put("d",true));}
        return validate(result);
    }
    public static JSONObject fromBackup(JSONObject backup,JSONObject file)throws Exception{
        if(backup.has("sync"))return new JSONObject(validate(backup.getJSONObject("sync")).toString());
        long time=0;try{time=java.time.Instant.parse(file.optString("createdTime")).toEpochMilli();}catch(Exception ignored){}
        String actor="legacy:"+file.optString("id","unknown");if(actor.length()>128)actor=actor.substring(0,128);
        return create(backup,actor,time);
    }
    public static JSONObject change(JSONObject doc,JSONObject before,JSONObject after,String actor,long time)throws Exception{
        validate(doc);JSONObject result=new JSONObject(doc.toString()),entries=result.getJSONObject("entries"),old=flatten(before),next=flatten(after);
        JSONArray latest=new JSONArray().put(0).put(0).put("");for(String key:keys(entries)){JSONArray stamp=entries.getJSONObject(key).getJSONArray("s");if(compare(stamp,latest)>0)latest=stamp;}
        JSONArray stamp=new JSONArray().put(Math.max(time,latest.getLong(0))).put(time>latest.getLong(0)?0:latest.getLong(1)+1).put(actor);
        Set<String> fields=new TreeSet<>();fields.addAll(keys(old));fields.addAll(keys(next));
        Set<String> forced=new HashSet<>();for(String base:new ArrayList<>(fields))if(base.startsWith("a/")&&base.split("/",-1).length==2)for(String[] group:GROUPS){boolean changed=false;for(String field:group)if(!equal(old.opt(base+"/"+field),next.opt(base+"/"+field)))changed=true;if(changed)for(String field:group){fields.add(base+"/"+field);forced.add(base+"/"+field);}}
        for(String key:fields){if(!forced.contains(key)&&old.has(key)==next.has(key)&&equal(old.opt(key),next.opt(key)))continue;
            JSONObject record=new JSONObject().put("s",stamp);
            if(next.has(key))record.put("v",next.get(key));else if(key.split("/",-1).length==2&&(key.startsWith("a/")||key.startsWith("r/")))record.put("v",false);else record.put("d",true);
            entries.put(key,record);
        }
        return validate(result);
    }
    public static JSONObject merge(JSONObject... docs)throws Exception{
        JSONObject result=empty(),entries=result.getJSONObject("entries");
        for(JSONObject doc:docs){if(doc==null)continue;validate(doc);JSONObject source=doc.getJSONObject("entries");for(String key:keys(source)){
            JSONObject value=source.getJSONObject(key),old=entries.optJSONObject(key);int order=old==null?1:compare(value.getJSONArray("s"),old.getJSONArray("s"));
            if(order>0||(order==0&&canonical(value).compareTo(canonical(old))>0))entries.put(key,new JSONObject(value.toString()));
        }}return result;
    }
    public static JSONObject backup(JSONObject doc)throws Exception{
        validate(doc);JSONObject data=new JSONObject(),reminders=new JSONObject().put("enabled",false).put("time","09:00"),assets=new JSONObject(),paid=new JSONObject(),alive=new JSONObject(),entries=doc.getJSONObject("entries");
        for(String key:keys(entries)){JSONObject record=entries.getJSONObject(key);if(record.optBoolean("d"))continue;String[] path=key.split("/",-1);for(int i=1;i<path.length;i++)path[i]=unescape(path[i]);
            Object value=record.get("v");if(path[0].equals("p"))data.put(path[1],value);else if(path[0].equals("n"))reminders.put(path[1],value);else if(path.length==2)alive.put(key,Boolean.TRUE.equals(value));
            else {JSONObject target=path[0].equals("a")?assets:paid;if(!target.has(path[1]))target.put(path[1],new JSONObject());target.getJSONObject(path[1]).put(path[2],value);}
        }
        JSONArray list=new JSONArray();Set<String> ids=new HashSet<>();for(String id:keys(assets))if(alive.optBoolean("a/"+escape(id))){list.put(assets.getJSONObject(id));ids.add(id);}
        JSONObject receipts=new JSONObject();for(String id:keys(paid))if(alive.optBoolean("r/"+escape(id))&&ids.contains(id.split(":",-1)[0]))receipts.put(id,paid.get(id));
        return new JSONObject().put("format",DriveRepository.FORMAT).put("version",1).put("data",data.put("scope","all").put("assets",list).put("paid",receipts)).put("notificationSettings",reminders).put("sync",new JSONObject(doc.toString()));
    }
}
