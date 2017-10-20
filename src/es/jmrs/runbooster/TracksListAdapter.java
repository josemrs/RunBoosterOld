package es.jmrs.runbooster;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class TracksListAdapter extends BaseAdapter  {

    Context context;
    int layoutResourceId;
    ArrayList<TrackItem> trackList = null;
    LayoutInflater inflater;
    	
    public TracksListAdapter(Context mContext, int layoutResourceId, ArrayList<TrackItem> data) {

        this.layoutResourceId = layoutResourceId;
        this.context = mContext;
        this.trackList = data;
        this.inflater = ((Activity) context).getLayoutInflater();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
    	ViewHolder  holder;

    	if(convertView==null){
            convertView = inflater.inflate(layoutResourceId, parent, false);
            
            holder = new ViewHolder();
            holder.title = (TextView) convertView.findViewById(R.id.itemTitleTextView);
            holder.artist = (TextView) convertView.findViewById(R.id.itemArtistTextView);
            holder.duration = (TextView) convertView.findViewById(R.id.itemDurationTextView);
            holder.art = (ImageView) convertView.findViewById(R.id.itemImageView);

            convertView.setTag(holder);
        }
    	else {
    		holder = (ViewHolder) convertView.getTag();
    	}

    	TrackItem trackItem = trackList.get(position);
    	
        holder.position = position;
        holder.title.setText(trackItem.getTitle());
        holder.artist.setText(trackItem.getArtist());
        holder.duration.setText(trackItem.getDuration());
        
        new ArtTask(position, holder)
        	.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
        					   trackItem.getArt());

        return convertView;
    }

    @Override
    public int getCount() {
        return trackList.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }
    
    @Override
    public boolean hasStableIds() {
      return true;
    }

	@Override
	public TrackItem getItem(int position) {
		// The list does not contain an item for the header, so -1 is needed.
		return trackList.get(position);
	}
		
	public void addItem(TrackItem track) {
		trackList.add(track);
		notifyDataSetChanged();
	}
	
	public void removeItem(int position) {
		// The list does not contain an item for the header, so -1 is needed.
		trackList.remove(position);
		notifyDataSetChanged();
	}
	
	private static class ViewHolder {
		public int position;
		public TextView title;
		public TextView artist;
		public TextView duration;
		public ImageView art;
	}
	
	private static class ArtTask extends AsyncTask<byte[], Integer, Bitmap> {
	    private int position;
	    private ViewHolder viewHolder;

	    public ArtTask(int position, ViewHolder holder) {
	    	this.position = position;
	    	this.viewHolder = holder;
	    }

		@Override
		protected Bitmap doInBackground(byte[]... artBytes) {
			if (artBytes[0] == null || artBytes[0].length == 0)
				return null;
			
	        return BitmapFactory.decodeByteArray(
	        		artBytes[0], 0, artBytes[0].length);
		}

	    protected void onPostExecute(Bitmap bitmap) {
	        if (viewHolder.position == position) {
	        	viewHolder.art.setImageBitmap(bitmap);
	        }
	    }
	}
}