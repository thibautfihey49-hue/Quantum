package com.v7.quantumfast;
import android.content.Context;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import org.json.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class GmailHelper {
    public static class MailItem { public String subject, from; public MailItem(String s,String f){subject=s; from=f;} }

    static String getToken(Context ctx, String email) throws Exception {
        return GoogleAuthUtil.getToken(ctx, email, "oauth2:https://www.googleapis.com/auth/gmail.readonly");
    }

    public static int fetchUnreadCount(Context ctx, String email) throws Exception {
        String token = getToken(ctx, email);
        URL url = new URL("https://gmail.googleapis.com/gmail/v1/users/me/labels/INBOX");
        HttpURLConnection con = (HttpURLConnection)url.openConnection();
        con.setRequestProperty("Authorization","Bearer "+token);
        con.setConnectTimeout(5000);
        BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
        StringBuilder sb=new StringBuilder(); String l; while((l=br.readLine())!=null) sb.append(l);
        JSONObject obj = new JSONObject(sb.toString());
        return obj.optInt("messagesUnread",0);
    }

    public static List<MailItem> fetchInbox(Context ctx, String email) throws Exception {
        String token = getToken(ctx, email);
        URL url = new URL("https://gmail.googleapis.com/gmail/v1/users/me/messages?q=in:inbox&maxResults=5");
        HttpURLConnection con = (HttpURLConnection)url.openConnection();
        con.setRequestProperty("Authorization","Bearer "+token);
        con.setConnectTimeout(5000);
        BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
        StringBuilder sb=new StringBuilder(); String l; while((l=br.readLine())!=null) sb.append(l);
        JSONObject resp = new JSONObject(sb.toString());
        JSONArray msgs = resp.optJSONArray("messages");
        List<MailItem> out = new ArrayList<>();
        if(msgs==null) return out;
        for(int i=0;i<Math.min(msgs.length(),3);i++){
            String id = msgs.getJSONObject(i).getString("id");
            URL url2 = new URL("https://gmail.googleapis.com/gmail/v1/users/me/messages/"+id+"?format=metadata&metadataHeaders=Subject&metadataHeaders=From");
            HttpURLConnection con2 = (HttpURLConnection)url2.openConnection();
            con2.setRequestProperty("Authorization","Bearer "+token);
            BufferedReader br2 = new BufferedReader(new InputStreamReader(con2.getInputStream()));
            StringBuilder sb2=new StringBuilder(); String l2; while((l2=br2.readLine())!=null) sb2.append(l2);
            JSONObject full = new JSONObject(sb2.toString());
            String subj="", from="";
            JSONObject payload = full.optJSONObject("payload");
            if(payload!=null){
                JSONArray headers = payload.optJSONArray("headers");
                if(headers!=null) for(int j=0;j<headers.length();j++){
                    JSONObject h=headers.getJSONObject(j);
                    if("Subject".equalsIgnoreCase(h.optString("name"))) subj=h.optString("value");
                    if("From".equalsIgnoreCase(h.optString("name"))) from=h.optString("value");
                }
            }
            out.add(new MailItem(subj, from));
        }
        return out;
    }
}
