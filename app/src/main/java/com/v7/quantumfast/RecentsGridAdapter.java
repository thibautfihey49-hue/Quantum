package com.v7.quantumfast;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import android.content.pm.ResolveInfo;
public class RecentsGridAdapter extends RecyclerView.Adapter<RecentsGridAdapter.H>{
 public static class H extends RecyclerView.ViewHolder{ ImageView ic; TextView lb; public H(View v){ super(v); ic=v.findViewById(R.id.icon); lb=v.findViewById(R.id.label);} }
 List<ResolveInfo> data; MainActivity act;
 public RecentsGridAdapter(MainActivity a, List<ResolveInfo> d){ act=a; data=d; }
 public H onCreateViewHolder(ViewGroup p,int t){ return new H(LayoutInflater.from(p.getContext()).inflate(R.layout.item_app,p,false)); }
 public void onBindViewHolder(H h,int pos){ try{ ResolveInfo ri=data.get(pos); h.lb.setText(ri.loadLabel(act.getPackageManager()).toString()); h.ic.setImageDrawable(ri.loadIcon(act.getPackageManager())); h.itemView.setOnClickListener(v->{ act.launchInstant(ri.activityInfo.packageName); }); }catch(Exception e){} }
 public int getItemCount(){ return Math.min(data.size(), 10); }
}
