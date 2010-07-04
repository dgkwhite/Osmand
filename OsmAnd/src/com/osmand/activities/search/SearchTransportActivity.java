/**
 * 
 */
package com.osmand.activities.search;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.osmand.Messages;
import com.osmand.OsmandSettings;
import com.osmand.R;
import com.osmand.ResourceManager;
import com.osmand.TransportIndexRepository;
import com.osmand.TransportIndexRepository.RouteInfoLocation;
import com.osmand.data.TransportRoute;
import com.osmand.data.TransportStop;
import com.osmand.osm.LatLon;
import com.osmand.osm.MapUtils;

/**
 * @author Maxim Frolov
 * 
 */
public class SearchTransportActivity extends ListActivity {



	private Button searchTransportLevel;
	
	
	private TextView searchArea;
	private TransportIndexRepository repo;
	
	private final static int finalZoom = 13;
	private final static int initialZoom = 17;
	private int zoom = initialZoom;
	private ProgressBar progress;
	private Thread thread;

	// TODO test when these args null
	private LatLon lastKnownMapLocation;
	private LatLon destinationLocation;
	
	private TransportStopAdapter stopsAdapter;
	private TransportRouteAdapter intermediateListAdapater;
	

	private static List<RouteInfoLocation> lastEditedRoute = new ArrayList<RouteInfoLocation>();

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.search_transport);
		searchTransportLevel = (Button) findViewById(R.id.SearchPOILevelButton);
		searchArea = (TextView) findViewById(R.id.SearchAreaText);
		searchTransportLevel.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if(isSearchFurtherAvailable()){
					zoom --;
					searchTransport();
				}
			}
		});
		progress = (ProgressBar) findViewById(R.id.ProgressBar);
		progress.setVisibility(View.INVISIBLE);
		stopsAdapter = new TransportStopAdapter(new ArrayList<RouteInfoLocation>());
		setListAdapter(stopsAdapter);
		
		
		ListView intermediateList = (ListView) findViewById(R.id.listView);
		intermediateListAdapater = new TransportRouteAdapter(lastEditedRoute);
		intermediateList.setAdapter(intermediateListAdapater);
		intermediateListAdapater.add(null);

		lastKnownMapLocation = OsmandSettings.getLastKnownMapLocation(this);
		destinationLocation = OsmandSettings.getPointToNavigate(this);
		searchTransport();
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		lastEditedRoute.clear();
		for(int i= 0; i< intermediateListAdapater.getCount(); i++){
			RouteInfoLocation item = intermediateListAdapater.getItem(i);
			if(item != null){
				lastEditedRoute.add(item);
			}
		}
	}
	
	public String getSearchArea(){
		return " < " + 125 * (1 << (17 - zoom)) + " " + Messages.getMessage(Messages.KEY_M); //$NON-NLS-1$//$NON-NLS-2$
	}
	public boolean isSearchFurtherAvailable() {
		return zoom >= finalZoom;
	}
	
	public void searchTransport(){
		// use progress
		stopsAdapter.clear();
		searchTransportLevel.setEnabled(false);
		searchArea.setText(getSearchArea());
		boolean routeCalculated = isRouteCalculated();
		if (!routeCalculated && getLocationToStart() != null) {
			final LatLon locationToStart = getLocationToStart();
			final LatLon locationToGo = getLocationToGo();
			List<TransportIndexRepository> rs = ResourceManager.getResourceManager().searchTransportRepositories(locationToStart.getLatitude(), 
					locationToStart.getLongitude());
			if(!rs.isEmpty()){
				repo = rs.get(0);
				progress.setVisibility(View.VISIBLE);
				synchronized (this) {
					final Thread previousThread = thread;
					thread = new Thread(new Runnable() {
						@Override
						public void run() {
							if (previousThread != null) {
								try {
									previousThread.join();
								} catch (InterruptedException e) {
								}
							}
							List<RouteInfoLocation> res = repo.searchTransportRouteStops(locationToStart.getLatitude(), locationToStart
									.getLongitude(), locationToGo, zoom);
							updateUIList(res);
						}
					}, "SearchingTransport"); //$NON-NLS-1$
					thread.start();
				}
				
			} else {
				repo = null;
			}
		}
	}
	
	protected void updateUIList(final List<RouteInfoLocation> stopsList){
		runOnUiThread(new Runnable(){
			@Override
			public void run() {
				stopsAdapter.setNewModel(stopsList);
				searchTransportLevel.setEnabled(isSearchFurtherAvailable());
				searchArea.setText(getSearchArea());
				progress.setVisibility(View.INVISIBLE);
			}
		});
	}
	
	public String getInformation(RouteInfoLocation route, List<TransportStop> stops, int position, boolean part){
		StringBuilder text = new StringBuilder(200);
		double dist = 0;
		int ind = 0;
		int stInd = stops.size();
		int eInd = stops.size();
		for (TransportStop s : stops) {
			if (s == route.getStart()) {
				stInd = ind;
			} else if (s == route.getStop()) {
				eInd = ind;
			}
			if (ind > stInd && ind <= eInd) {
				dist += MapUtils.getDistance(stops.get(ind - 1).getLocation(), s.getLocation());
			}
			ind++;
		}
		text.append(getString(R.string.transport_route_distance)).append(" ").append(MapUtils.getFormattedDistance((int) dist));  //$NON-NLS-1$/
		if(!part){
			text.append(", ").append(getString(R.string.transport_stops_to_pass)).append(" ").append(eInd - stInd);   //$NON-NLS-1$ //$NON-NLS-2$
			String before = MapUtils.getFormattedDistance((int) MapUtils.getDistance(getEndStop(position - 1), route.getStart().getLocation()));
			String after = MapUtils.getFormattedDistance((int) MapUtils.getDistance(getStartStop(position + 1), route.getStop().getLocation()));
			text.append(", ").append(getString(R.string.transport_to_go_before)).append(" ").append(before).append(", ");  //$NON-NLS-2$//$NON-NLS-3$ //$NON-NLS-1$
			text.append(getString(R.string.transport_to_go_after)).append(" ").append(after);  //$NON-NLS-1$
		}
		
		return text.toString();
	}
	

	public void onListItemClick(ListView parent, View v, int position, long id) {
		final RouteInfoLocation item = ((TransportStopAdapter)getListAdapter()).getItem(position);
		Builder builder = new AlertDialog.Builder(this);
		List<String> items = new ArrayList<String>();
		final List<TransportStop> stops = item.getDirection() ? item.getRoute().getForwardStops() : item.getRoute().getBackwardStops();
		LatLon locationToGo = getLocationToGo();
		LatLon locationToStart = getLocationToStart();
		builder.setTitle(getString(R.string.transport_stop_to_go_out)+"\n"+getInformation(item, stops, getCurrentRouteLocation(), true)); //$NON-NLS-1$
		int ind = 0;
		for(TransportStop st : stops){
			StringBuilder n = new StringBuilder(50);
			n.append(ind++);
			if(st == item.getStop()){
				n.append("!! "); //$NON-NLS-1$
			} else {
				n.append(". "); //$NON-NLS-1$
			}
			String name = st.getName(OsmandSettings.usingEnglishNames(this));
			if(locationToGo != null){
				n.append(name).append(" - ["); //$NON-NLS-1$
				n.append(MapUtils.getFormattedDistance((int) MapUtils.getDistance(locationToGo, st.getLocation()))).append("]"); //$NON-NLS-1$ 
			} else {
				n.append("[").append(MapUtils.getFormattedDistance((int) MapUtils.getDistance(locationToStart, st.getLocation()))).append("] - "); //$NON-NLS-1$ //$NON-NLS-2$
				n.append(name);
			}
			items.add(n.toString());
		}
		builder.setItems(items.toArray(new String[items.size()]), new DialogInterface.OnClickListener(){

			@Override
			public void onClick(DialogInterface dialog, int which) {
				int i = which;
				if(i >= 0){
					TransportStop stop = stops.get(i);
					showContextMenuOnStop(stop, item, i);
				}
			}
			
		});
		builder.show();
	}
	
	
	public void showContextMenuOnStop(final TransportStop stop, final RouteInfoLocation route, final int stopInd) {
		Builder b = new AlertDialog.Builder(this);
		b.setItems(new String[] { getString(R.string.transport_finish_search), getString(R.string.transport_search_before), getString(R.string.transport_search_after) },
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						int currentRouteCalculation = getCurrentRouteLocation();
						route.setStop(stop);
						route.setStopNumbers(stopInd);
						if (which == 0) {
							intermediateListAdapater.insert(route, currentRouteCalculation);
							intermediateListAdapater.remove(null);
							currentRouteCalculation = -1;
						} else if (which == 1) {
							intermediateListAdapater.insert(route, currentRouteCalculation + 1);
						} else if (which == 2) {
							intermediateListAdapater.insert(route, currentRouteCalculation);
						}
						// layout
						zoom = initialZoom;
						searchTransport();
					}

				});
		b.show();
	}
	
	public void showContextMenuOnRoute(final RouteInfoLocation route, final int routeInd) {
		Builder b = new AlertDialog.Builder(this);
		List<TransportStop> stops = route.getDirection() ? route.getRoute().getForwardStops() : route.getRoute().getBackwardStops();
		boolean en = OsmandSettings.isUsingInternetToDownloadTiles(this);
		
		String info = getInformation(route, stops, routeInd, false);
		StringBuilder txt = new StringBuilder(300);
		txt.append(info);
		boolean start = false;
		for(TransportStop s : stops){
			if(s == route.getStart()){
				start = true;
			}
			if(start){
				txt.append("\n").append(getString(R.string.transport_Stop)).append(" : ").append(s.getName(en)); //$NON-NLS-1$ //$NON-NLS-2$
			}
			if(s == route.getStop()){
				break;
			}
		}
		
		b.setMessage(txt.toString());
		b.setPositiveButton(getString(R.string.default_buttons_ok), null);
		b.setNeutralButton(getString(R.string.transport_search_before), new DialogInterface.OnClickListener(){
			@Override
			public void onClick(DialogInterface dialog, int which) {
				int toInsert = routeInd;
				int c = getCurrentRouteLocation();
				if(c >= 0 && c < routeInd){
					toInsert --;
				}
				intermediateListAdapater.remove(null);
				intermediateListAdapater.insert(null, routeInd);
				zoom = initialZoom;
				searchTransport();	
			}
		});
		b.setNegativeButton(getString(R.string.transport_search_after), new DialogInterface.OnClickListener(){
			@Override
			public void onClick(DialogInterface dialog, int which) {
				int toInsert = routeInd;
				int c = getCurrentRouteLocation();
				if(c > routeInd || c == -1){
					toInsert ++;
				}
				intermediateListAdapater.remove(null);
				intermediateListAdapater.insert(null, toInsert);
				zoom = initialZoom;
				searchTransport();	
			}
		});
		b.show();
	}
	
	public int getCurrentRouteLocation(){
		return intermediateListAdapater.getPosition(null);
	}

	public boolean isRouteCalculated(){
		return getCurrentRouteLocation() == -1;
	}
	
	public LatLon getLocationToStart() {
		return getEndStop(getCurrentRouteLocation() - 1);
	}
	
	public LatLon getLocationToGo() {
		return getStartStop(getCurrentRouteLocation() + 1);
	}
	
	// TODO always check for null
	public LatLon getStartStop(int position){
		if(position == intermediateListAdapater.getCount()){
			return destinationLocation;
		}
		RouteInfoLocation item = intermediateListAdapater.getItem(position);
		if(item == null){
			return getStartStop(position + 1);
		}
		return item.getStart().getLocation();
	}

	
	// TODO always check for null
	public LatLon getEndStop(int position){
		if(position == -1){
			return lastKnownMapLocation;
		}
		RouteInfoLocation item = intermediateListAdapater.getItem(position);
		if(item == null){
			return getEndStop(position -1);
		}
		return item.getStop().getLocation();
	}

	class TransportStopAdapter extends ArrayAdapter<RouteInfoLocation> {
		private List<RouteInfoLocation> model;

		TransportStopAdapter(List<RouteInfoLocation> list) {
			super(SearchTransportActivity.this, R.layout.search_transport_list_item, list);
			model = list;
		}

		public void setNewModel(List<RouteInfoLocation> stopsList) {
			this.model = stopsList;
			setNotifyOnChange(false);
			((TransportStopAdapter) getListAdapter()).clear();
			for (RouteInfoLocation obj : stopsList) {
				this.add(obj);
			}
			this.notifyDataSetChanged();
			setNotifyOnChange(true);
		}
		
		public List<RouteInfoLocation> getModel() {
			return model;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			View row = convertView;
			if (row == null) {
				LayoutInflater inflater = getLayoutInflater();
				row = inflater.inflate(R.layout.search_transport_list_item, parent, false);
			}
			LatLon locationToGo = getLocationToGo();
			LatLon locationToStart = getLocationToStart();
			
			TextView label = (TextView) row.findViewById(R.id.label);
			TextView distanceLabel = (TextView) row.findViewById(R.id.distance);
			ImageView icon = (ImageView) row.findViewById(R.id.search_icon);
			RouteInfoLocation stop = getItem(position);

			TransportRoute route = stop.getRoute();
			StringBuilder labelW = new StringBuilder(150);
			labelW.append(route.getType()).append(" ").append(route.getRef()); //$NON-NLS-1$
			labelW.append(" - ["); //$NON-NLS-1$
			
			if (locationToGo != null) {
				labelW.append(MapUtils.getFormattedDistance(stop.getDistToLocation()));
			} else {
				labelW.append(getString(R.string.transport_search_none));
			}
			labelW.append("]\n").append(route.getName(OsmandSettings.usingEnglishNames(SearchTransportActivity.this))); //$NON-NLS-1$
			label.setText(labelW.toString());
			// TODO icons
			if (locationToGo != null && stop.getDistToLocation() < 400) {
				icon.setImageResource(R.drawable.poi);
			} else {
				icon.setImageResource(R.drawable.closed_poi);
			}
			int dist = (int) (MapUtils.getDistance(stop.getStart().getLocation(), locationToStart));
			distanceLabel.setText(" " + MapUtils.getFormattedDistance(dist)); //$NON-NLS-1$

			return (row);
		}
	}
	
	
	class TransportRouteAdapter extends ArrayAdapter<RouteInfoLocation> {
		TransportRouteAdapter(List<RouteInfoLocation> list) {
			super(SearchTransportActivity.this, R.layout.search_transport_route_item, list);
		}

		public View getView(final int position, View convertView, ViewGroup parent) {
			View row = convertView;
			int currentRouteLocation = getCurrentRouteLocation();
			if(position == currentRouteLocation){
				TextView text = new TextView(getContext());
				LatLon st = getStartStop(position + 1);
				LatLon end = getEndStop(position - 1);

				if(st != null){
					int dist = (int) MapUtils.getDistance(st, end);
					text.setText(MessageFormat.format(getString(R.string.transport_searching_route), MapUtils.getFormattedDistance(dist)));
				} else {
					text.setText(getString(R.string.transport_searching_transport));
				}
				text.setTextSize(21);
				text.setTypeface(null, Typeface.ITALIC);
				text.setOnClickListener(new View.OnClickListener(){

					@Override
					public void onClick(View v) {
						if(intermediateListAdapater.getCount() > 1){
							intermediateListAdapater.remove(null);
							searchTransport();
						}
						
					}
					
				});
				return text;
			}
			
			if (row == null || row instanceof TextView) {
				LayoutInflater inflater = getLayoutInflater();
				row = inflater.inflate(R.layout.search_transport_route_item, parent, false);
			}
			final RouteInfoLocation info = getItem(position);
			TextView label = (TextView) row.findViewById(R.id.label);
			ImageButton icon = (ImageButton) row.findViewById(R.id.remove);
			
			TransportRoute route = info.getRoute();
			icon.setVisibility(View.VISIBLE);
			StringBuilder labelW = new StringBuilder(150);
			labelW.append(route.getType()).append(" ").append(route.getRef()); //$NON-NLS-1$
			boolean en = OsmandSettings.usingEnglishNames(SearchTransportActivity.this);
			labelW.append(" : ").append(info.getStart().getName(en)).append(" - ").append(info.getStop().getName(en)); //$NON-NLS-1$ //$NON-NLS-2$
			// additional information  if route is calculated
			if (currentRouteLocation == -1) {
				labelW.append(" ("); //$NON-NLS-1$
				labelW.append(info.getStopNumbers()).append(" ").append(getString(R.string.transport_stops)).append(", "); //$NON-NLS-1$ //$NON-NLS-2$
				int startDist = (int) MapUtils.getDistance(getEndStop(position - 1), info.getStart().getLocation());
				labelW.append(getString(R.string.transport_to_go_before)).append(" ").append(MapUtils.getFormattedDistance(startDist)); //$NON-NLS-1$
				if (position == getCount() - 1) {
					int endDist = (int) MapUtils.getDistance(getStartStop(position + 1), info.getStop().getLocation());
					labelW.append(", ").append(getString(R.string.transport_to_go_after)).append(" ").append(MapUtils.getFormattedDistance(endDist));  //$NON-NLS-1$ //$NON-NLS-2$
				}

				labelW.append(")"); //$NON-NLS-1$ 
				
			}
			label.setText(labelW.toString());
			icon.setOnClickListener(new View.OnClickListener(){
				@Override
				public void onClick(View v) {
					int p = position;
					intermediateListAdapater.remove(null);
					if(!isRouteCalculated() && getCurrentRouteLocation() < p){
						p--;
					}
					intermediateListAdapater.insert(null, p);
					intermediateListAdapater.remove(info);
					intermediateListAdapater.notifyDataSetChanged();
					zoom = initialZoom; 
					searchTransport();
					
				}
				
			});
			View.OnClickListener clickListener = new View.OnClickListener(){
				@Override
				public void onClick(View v) {
					showContextMenuOnRoute(info, position);
				}
				
			};
			label.setOnClickListener(clickListener);
			return row;
		}
	}

}
