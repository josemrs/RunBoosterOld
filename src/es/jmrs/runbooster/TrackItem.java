package es.jmrs.runbooster;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

public class TrackItem implements Parcelable {

	public final static String FILE_PATH = "filePath";
	public final static String TITLE = "track";
	public final static String ALBUM = "album";
	public final static String ARTIST = "artist";
	public final static String DURATION = "durtion";
	public final static String ART = "art";

    private String title;
    private String album;
    private String artist;
    private String duration;
    private byte[] art;
    private String filePath;

    public static final Parcelable.Creator<TrackItem> CREATOR = 
    		new Parcelable.Creator<TrackItem>() {
        public TrackItem createFromParcel(Parcel in) {
            return new TrackItem(in); 
        }

        public TrackItem[] newArray(int size) {
            return new TrackItem[size];
        }
    };

    public TrackItem(Parcel in) {
    	
        Bundle bundle = in.readBundle();
        this.filePath = bundle.getString(FILE_PATH);
        this.title = bundle.getString(TITLE);
        this.album = bundle.getString(ALBUM);
        this.artist = bundle.getString(ARTIST);
        this.duration = bundle.getString(DURATION);
        this.art = bundle.getByteArray(ART);
    }
    
    public TrackItem(String filePath, Bundle metadata) {
    	
        this.filePath = filePath;
        
        this.title = "";
        this.artist = "";
        this.album = ""; 
        this.duration = "00:00";
    	this.art = null;
        
        if (metadata == null)
        	return;
        
        if (metadata.containsKey(TITLE))
        	this.title = metadata.getString(TITLE);
        if (metadata.containsKey(ALBUM))
        	this.album = metadata.getString(ALBUM);
        if (metadata.containsKey(ARTIST))
        	this.artist = metadata.getString(ARTIST);
        if (metadata.containsKey(DURATION))
        	this.duration = metadata.getString(DURATION);
        if (metadata.containsKey(ART))
        	this.art = metadata.getByteArray(ART);
    }
    
    @Override
    public boolean equals(Object other){
        if (other == null) return false;
        if (other == this) return true;
        if (!(other instanceof TrackItem))return false;
        TrackItem otherTrackItem = (TrackItem)other;
        
        if (!otherTrackItem.filePath.equals(this.filePath)) return false;

        return true;
    }
    
    @Override
    public int hashCode() {
    	assert false : "hashCode not designed";
    	return 0; // any arbitrary constant will do
    }
    
	@Override
	public int describeContents() {
		return 0;
	}

	@Override
    public void writeToParcel(Parcel dest, int flags) {
		
		Bundle bundle = new Bundle();
		bundle.putString(FILE_PATH, this.filePath);
		bundle.putString(TITLE, this.title);
		bundle.putString(ALBUM, this.album);
		bundle.putString(ARTIST, this.artist);
		bundle.putString(DURATION, this.duration);
		bundle.putByteArray(ART, this.art);
        dest.writeBundle(bundle);
    }
	
	public String getFilePath() {
		return filePath;
	}
	
	public String getTitle() {
		return title;
	}
	
	public String getAlbum() {
		return album;
	}
	
	public String getArtist() {
		return artist;
	}
	
	public String getDuration() {
		return duration;
	}
	
	public byte[] getArt() {
		return art;
	}
}
