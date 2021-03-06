package com.yuphilip.controller.activities;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.ProgressBar;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.codepath.asynchttpclient.callback.JsonHttpResponseHandler;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.yuphilip.R;
import com.yuphilip.controller.adapters.TweetsAdapter;
import com.yuphilip.controller.fragments.ComposeFragment;
import com.yuphilip.databinding.ActivityTimelineBinding;
import com.yuphilip.model.Tweet;
import com.yuphilip.model.TweetDao;
import com.yuphilip.model.TweetWithUser;
import com.yuphilip.model.User;
import com.yuphilip.model.helper.EndlessRecyclerViewScrollListener;
import com.yuphilip.model.net.TwitterApp;
import com.yuphilip.model.net.TwitterClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.parceler.Parcels;

import java.util.ArrayList;
import java.util.List;

import okhttp3.Headers;

public class TimelineActivity extends AppCompatActivity {

    //region Properties

    private static final String TAG = "TimelineActivity";

    private TweetDao tweetDao;
    private TwitterClient client;
    private RecyclerView rvTweets;
    private List<Tweet> tweets;
    private TweetsAdapter adapter;
    private SwipeRefreshLayout swipeContainer;
    private ProgressBar progressBar;

    //endregion

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        com.yuphilip.databinding.ActivityTimelineBinding binding = DataBindingUtil.setContentView(this, R.layout.activity_timeline);

        client = TwitterApp.getRestClient(this);
        tweetDao = ((TwitterApp) getApplicationContext()).getMyDatabase().tweetDao();

        swipeContainer = binding.swipeContainer;
        // Configure the refreshing colors
        swipeContainer.setColorSchemeResources(android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light);
        swipeContainer.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                Log.i(TAG, "fetching new data!");
                populateHomeTimeline();
            }
        });

        // Progress bar
        progressBar = binding.progressBar;
        progressBar.setVisibility(View.INVISIBLE);

        // Find the toolbar view inside the activity layout
        Toolbar toolbar = binding.toolbar;
        // Sets the Toolbar to act as the ActionBar for this Activity window.
        // Make sure the toolbar exists in the activity and is not null
        setSupportActionBar(toolbar);

        // Handle compose FAB
        FloatingActionButton fab = binding.fabCompose;

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "FAB compose button pressed...");
                showTweetDialog();
            }
        });

        // Find the recycler view
        rvTweets = binding.rvTweets;

        // Init the list of the tweets and adapter
        tweets = new ArrayList<>();
        adapter = new TweetsAdapter(this, tweets);

        // Recycler view setup: layout manager and the adapter
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        rvTweets.setLayoutManager(layoutManager);
        rvTweets.setAdapter(adapter);

        EndlessRecyclerViewScrollListener scrollListener = new EndlessRecyclerViewScrollListener(layoutManager) {
            @Override
            public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                Log.i(TAG, "onLoadMore:" + page);
                loadMoreData();
            }
        };

        // Adds the scroll listener to recyclerview
        rvTweets.addOnScrollListener(scrollListener);

        // Query for existing tweets in the database
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "Showing data from database");

                List<TweetWithUser> tweetWithUsers = tweetDao.recentItems();
                List<Tweet> tweetsFromDB = TweetWithUser.getTweetList(tweetWithUsers);

                adapter.clear();
                adapter.addAll(tweetsFromDB);
            }
        });

        populateHomeTimeline();

        swipeContainer.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                progressBar.setVisibility(View.VISIBLE);
                populateHomeTimeline();
            }
        });

    }

    private void showTweetDialog() {

        FragmentManager fragmentManager = getSupportFragmentManager();

        ComposeFragment composeFragment = ComposeFragment.newInstance("Compose Tweet");

        composeFragment.show(fragmentManager, "compose_fragment");

    }

    private void loadMoreData() {

        // Send an API request to retrieve appropriate paginated data
        client.getNextPageOfTweets(new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Headers headers, JSON json) {
                // Log.i(TAG, "onSuccess for loadMoreData" + json.toString());
                //  --> Send the request including an offset value (i.e `page`) as a query parameter.
                //  --> Deserialize and construct new model objects from the API response
                JSONArray jsonArray = json.jsonArray;

                try {
                    List<Tweet> tweets = Tweet.fromJsonArray(jsonArray);
                    //  --> Append the new data objects to the existing set of items inside the array of items
                    //  --> Notify the adapter of the new items made with `notifyItemRangeInserted()`
                    adapter.addAll(tweets);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailure(int statusCode, Headers headers, String response, Throwable throwable) {
                Log.e(TAG, "onFailure for loadMoreData", throwable);
            }
        }, tweets.get(tweets.size() - 1).id);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {

        int REQUEST_CODE = 20;
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {
            // Get data from the intent (tweet)
            Tweet tweet = Parcels.unwrap(data.getParcelableExtra("tweet"));

            // Update the RV with the tweet
            // Modify data source of tweets
            tweets.add(0, tweet);

            // Update the adapter
            adapter.notifyItemInserted(0);
            rvTweets.smoothScrollToPosition(0);
        }

        super.onActivityResult(requestCode, resultCode, data);

    }

    private void populateHomeTimeline() {

        client.getHomeTimeline(new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Headers headers, JSON json) {
                // Log.i(TAG, "onSuccess!" + json.toString());
                JSONArray jsonArray = json.jsonArray;

                try {
                    final List<Tweet> tweetsFromNetwork = Tweet.fromJsonArray(jsonArray);
                    final List<User> usersFromNetwork = User.fromJsonTweetArray(tweetsFromNetwork);

                    adapter.clear();
                    adapter.addAll(tweetsFromNetwork);
                    swipeContainer.setRefreshing(false);

                    AsyncTask.execute(new Runnable() {
                        @Override
                        public void run() {
                            Log.i(TAG, "Saving data into database");

                            tweetDao.insertModel(usersFromNetwork.toArray(new User[0]));
                            tweetDao.insertModel(tweetsFromNetwork.toArray(new Tweet[0]));
                        }
                    });
                } catch (JSONException e) {
                    Log.e(TAG, "Json exception", e);
                }

                progressBar.setVisibility(View.INVISIBLE);

            }

            @Override
            public void onFailure(int statusCode, Headers headers, String response, Throwable throwable) {
                Log.e(TAG, "onFailure!" + response, throwable);
            }
        });

    }

}
