/*******************************************************************************
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package li.barter.fragments;

import li.barter.R;
import li.barter.adapters.BooksAroundMeAdapter;
import li.barter.data.DatabaseColumns;
import li.barter.data.SQLConstants;
import li.barter.data.SQLiteLoader;
import li.barter.data.ViewUserBooksWithLocations;
import li.barter.http.IBlRequestContract;
import li.barter.http.ResponseInfo;
import li.barter.utils.AppConstants.FragmentTags;
import li.barter.utils.AppConstants.Keys;
import li.barter.utils.AppConstants.Loaders;
import li.barter.utils.Logger;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import com.haarman.listviewanimations.swinginadapters.AnimationAdapter;
import com.haarman.listviewanimations.swinginadapters.prepared.SwingBottomInAnimationAdapter;

/**
 * @author Anshul Kamboj
 */

@FragmentTransition(enterAnimation = R.anim.slide_in_from_right, exitAnimation = R.anim.zoom_out, popEnterAnimation = R.anim.zoom_in, popExitAnimation = R.anim.slide_out_to_right)
public class MyBooksFragment extends AbstractBarterLiFragment implements
 LoaderCallbacks<Cursor>,
OnItemClickListener {

	private static final String           TAG = "MyBooksFragment";

	
	private ListView                      mBooksAroundMeListView;

	/**
	 * {@link BooksAroundMeAdapter} instance for the Books
	 */
	private BooksAroundMeAdapter          mBooksAroundMeAdapter;

	/**
	 * {@link AnimationAdapter} implementation to provide appearance animations
	 * for the book items as they are brought in
	 */
	private SwingBottomInAnimationAdapter mSwingBottomInAnimationAdapter;

	private String                        mUserId;

	private View                          mProfileDetails;

	@Override
	public View onCreateView(final LayoutInflater inflater,
			final ViewGroup container, final Bundle savedInstanceState) {
		init(container, savedInstanceState);
		setHasOptionsMenu(true);
		final View view = inflater.inflate(R.layout.fragment_my_profile, null);

		mBooksAroundMeListView = (ListView) view
				.findViewById(R.id.list_my_books);

		setActionBarTitle(R.string.profilepage_title);

		final Bundle extras = getArguments();

		if (extras != null) {
			mUserId = extras.getString(Keys.USER_ID);

		}


		
		loadMyBooks();
		
		mBooksAroundMeListView.addHeaderView(mProfileDetails, null, false);
		mBooksAroundMeAdapter = new BooksAroundMeAdapter(getActivity());
		mSwingBottomInAnimationAdapter = new SwingBottomInAnimationAdapter(mBooksAroundMeAdapter, 150, 500);
		mSwingBottomInAnimationAdapter.setAbsListView(mBooksAroundMeListView);
		mBooksAroundMeListView.setAdapter(mBooksAroundMeAdapter);

		mBooksAroundMeListView.setOnItemClickListener(this);

		setActionBarDrawerToggleEnabled(false);

		return view;
	}


	


	

	@Override
	public void onStop() {
		super.onStop();
	}

	@Override
	protected Object getVolleyTag() {
		return TAG;
	}

	@Override
	public void onSuccess(final int requestId,
			final IBlRequestContract request,
			final ResponseInfo response) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onBadRequestError(final int requestId,
			final IBlRequestContract request, final int errorCode,
			final String errorMessage, final Bundle errorResponseBundle) {
		// TODO Auto-generated method stub

	}



	/**
	 * Fetches books owned by the current user
	 */

	private void loadMyBooks() {
		getLoaderManager().restartLoader(Loaders.GET_MY_BOOKS, null, this);
	}

	@Override
	public Loader<Cursor> onCreateLoader(final int loaderId, final Bundle args) {
		if (loaderId == Loaders.GET_MY_BOOKS) {
			final String selection = DatabaseColumns.USER_ID
					+ SQLConstants.EQUALS_ARG;
			final String[] argsId = new String[1];
			argsId[0]=mUserId;
			return new SQLiteLoader(getActivity(), false, ViewUserBooksWithLocations.NAME, null, selection, argsId, null, null, null, null);
		}

		
		else {


			return null;
		}
	}

	@Override
	public void onLoadFinished(final Loader<Cursor> loader, final Cursor cursor) {
		if (loader.getId() == Loaders.GET_MY_BOOKS) {
			Logger.d(TAG, "Cursor Loaded with count: %d", cursor.getCount());
			mBooksAroundMeAdapter.swapCursor(cursor);
		}
		
		
	}

	@Override
	public void onLoaderReset(final Loader<Cursor> loader) {
		if (loader.getId() == Loaders.GET_MY_BOOKS) {
			mBooksAroundMeAdapter.swapCursor(null);
			mBooksAroundMeListView.setAdapter(null);
			mBooksAroundMeListView.removeHeaderView(mProfileDetails);
		}
	}

	@Override
	public void onItemClick(final AdapterView<?> parent, final View view,
			final int position, final long id) {

		if (parent.getId() == R.id.list_my_books) {
			/*
			 * Subtract from position to account for header
			 */
			final Cursor cursor = (Cursor) mBooksAroundMeAdapter
					.getItem(position
							- mBooksAroundMeListView
							.getHeaderViewsCount());

			final String bookId = cursor.getString(cursor
					.getColumnIndex(DatabaseColumns.BOOK_ID));

			final Bundle showBooksArgs = new Bundle();
			showBooksArgs.putString(Keys.BOOK_ID, bookId);
			showBooksArgs.putString(Keys.USER_ID, mUserId);
			showBooksArgs.putBoolean(Keys.OTHER_PROFILE_FLAG, true);

			loadFragment(mContainerViewId, (AbstractBarterLiFragment) Fragment
					.instantiate(getActivity(), BookDetailFragment.class
							.getName(), showBooksArgs), FragmentTags.MY_BOOK_FROM_PROFILE, true, FragmentTags.BS_EDIT_PROFILE);
		}
	}

}