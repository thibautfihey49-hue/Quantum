package com.v7.quantumfast;
import android.content.Context;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.*;
import java.util.*;

public class GmailHelper {
    public static Gmail getService(Context ctx, String email){
        GoogleAccountCredential cred = GoogleAccountCredential.usingOAuth2(ctx, Collections.singleton(GmailScopes.GMAIL_READONLY));
        cred.setSelectedAccountName(email);
        return new Gmail.Builder(AndroidHttp.newCompatibleTransport(), JacksonFactory.getDefaultInstance(), cred).setApplicationName("V7-QUANTUM-FAST").build();
    }
    public static class MailItem { public String subject, from; public MailItem(String s,String f){subject=s; from=f;} }
    public static List<MailItem> fetchInbox(Context ctx, String email) throws Exception {
        Gmail service = getService(ctx, email);
        ListMessagesResponse resp = service.users().messages().list("me").setQ("in:inbox").setMaxResults(5L).execute();
        List<MailItem> out = new ArrayList<>();
        if(resp.getMessages()!=null){
            for(Message m: resp.getMessages()){
                Message full = service.users().messages().get("me", m.getId()).setFormat("metadata").setMetadataHeaders(Arrays.asList("Subject","From")).execute();
                String subj="", from="";
                if(full.getPayload()!=null && full.getPayload().getHeaders()!=null){
                    for(MessagePartHeader h: full.getPayload().getHeaders()){
                        if("Subject".equalsIgnoreCase(h.getName())) subj=h.getValue();
                        if("From".equalsIgnoreCase(h.getName())) from=h.getValue();
                    }
                }
                out.add(new MailItem(subj, from));
                if(out.size()>=3) break;
            }
        }
        return out;
    }
    public static int fetchUnreadCount(Context ctx, String email) throws Exception {
        Gmail service = getService(ctx, email);
        Label inbox = service.users().labels().get("me","INBOX").execute();
        return inbox.getMessagesUnread()!=null?inbox.getMessagesUnread():0;
    }
}
