package com.v7.quantumfast;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import com.google.android.gms.auth.api.signin.*;
import com.google.android.gms.common.api.Scope;
import com.google.api.services.gmail.GmailScopes;

public class MainActivity extends Activity {
    public static final int RC_SIGN_IN = 1001;
    private GoogleSignInClient gClient;
    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
           .requestEmail()
           .requestScopes(new Scope(GmailScopes.GMAIL_READONLY))
           .build();
        gClient = GoogleSignIn.getClient(this, gso);
        if(b==null) getFragmentManager().beginTransaction().replace(R.id.mainContainer, new Page3Fragment()).commit();
    }
    public void signInGmail(){ startActivityForResult(gClient.getSignInIntent(), RC_SIGN_IN); }
    public void signOutGmail(){ gClient.signOut(); }
    @Override protected void onActivityResult(int rc,int res, Intent data){
        super.onActivityResult(rc,res,data);
        if(rc==RC_SIGN_IN){
            // Fragment va se refresh auto via onResume
        }
    }
    public GoogleSignInAccount getAccount(){ return GoogleSignIn.getLastSignedInAccount(this); }
}
