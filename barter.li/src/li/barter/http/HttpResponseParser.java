/**
 * Copyright 2014, barter.li
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
 */

package li.barter.http;

import org.apache.http.HttpStatus;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.os.Bundle;

import li.barter.data.DBInterface;
import li.barter.data.DatabaseColumns;
import li.barter.data.SQLConstants;
import li.barter.data.TableLocations;
import li.barter.data.TableSearchBooks;
import li.barter.http.HttpConstants.RequestId;
import li.barter.parcelables.Hangout;
import li.barter.utils.Logger;

/**
 * Class that reads an API response and parses it and stores it in the database
 * 
 * @author Vinay S Shenoy
 */
public class HttpResponseParser {

    private static final String TAG = "HttpResponseParser";

    /**
     * Parses the string response(when the request was successful -
     * {@linkplain HttpStatus#SC_OK} was returned) for a particular
     * {@linkplain RequestId} and returns the response data
     * 
     * @param requestId
     * @param response
     * @return
     * @throws JSONException
     */
    public ResponseInfo getSuccessResponse(final int requestId,
                    final String response) throws JSONException {

        Logger.d(TAG, "Request Id %d\nResponse %s", requestId, response);
        switch (requestId) {

            case RequestId.CREATE_BOOK: {
                return parseCreateBookResponse(response);
            }

            case RequestId.GET_BOOK_INFO: {
                return parseGetBookInfoResponse(response);
            }

            case RequestId.SEARCH_BOOKS: {
                //Delete the current search results before parsing the old ones
                DBInterface.delete(TableSearchBooks.NAME, null, null, true);
                return parseSearchBooksResponse(response);
            }

            case RequestId.CREATE_USER: {
                return parseCreateUserResponse(response);
            }

            case RequestId.HANGOUTS: {
                return parseHangoutsResponse(response);
            }

            case RequestId.SET_USER_PREFERRED_LOCATION: {
                return parseSetUserPreferredLocationResponse(response);
            }

            default: {
                throw new IllegalArgumentException("Unknown request Id:"
                                + requestId);
            }
        }
    }

    /**
     * Method for parsing the create user/login response
     * 
     * @param response
     * @return
     * @throws JSONException If the Json response is malformed
     */
    private ResponseInfo parseCreateUserResponse(final String response)
                    throws JSONException {

        final ResponseInfo responseInfo = new ResponseInfo();

        final JSONObject responseObject = new JSONObject(response);

        final JSONObject userObject = JsonUtils
                        .readJSONObject(responseObject, HttpConstants.USER, true, true);

        final Bundle responseBundle = new Bundle();
        responseBundle.putString(HttpConstants.ID, JsonUtils
                        .readString(userObject, HttpConstants.ID, true, true));
        responseBundle.putString(HttpConstants.AUTH_TOKEN, JsonUtils
                        .readString(userObject, HttpConstants.AUTH_TOKEN, true, true));
        responseBundle.putString(HttpConstants.EMAIL, JsonUtils
                        .readString(userObject, HttpConstants.EMAIL, true, true));
        responseBundle.putString(HttpConstants.DESCRIPTION, JsonUtils
                        .readString(userObject, HttpConstants.DESCRIPTION, false, false));
        responseBundle.putString(HttpConstants.FIRST_NAME, JsonUtils
                        .readString(userObject, HttpConstants.FIRST_NAME, false, false));
        responseBundle.putString(HttpConstants.LAST_NAME, JsonUtils
                        .readString(userObject, HttpConstants.LAST_NAME, false, false));

        final JSONObject locationObject = JsonUtils
                        .readJSONObject(userObject, HttpConstants.LOCATION, false, false);

        String locationId = null;
        if (locationObject != null) {
            locationId = parseAndStoreLocation(locationObject);
        }

        responseBundle.putString(HttpConstants.LOCATION, locationId); //Would like to use location id, but server just sends id for location
        responseInfo.responseBundle = responseBundle;
        return responseInfo;
    }

    /**
     * Reads out a Location object from Json, stores it the DB and returns the
     * location id
     * 
     * @param locationObject The Location object
     * @return The id of the parsed location
     * @throws JSONException If the Json is invalid
     */
    private String parseAndStoreLocation(final JSONObject locationObject)
                    throws JSONException {

        final ContentValues values = new ContentValues();
        final String locationId = readLocationDetailsIntoContentValues(locationObject, values, true);
        final String selection = DatabaseColumns.LOCATION_ID
                        + SQLConstants.EQUALS_ARG;
        final String[] args = new String[] {
            locationId
        };

        //Update the locations table if the location already exists
        if (DBInterface.update(TableLocations.NAME, values, selection, args, true) == 0) {

            //Location was not present, insert into locations table
            DBInterface.insert(TableLocations.NAME, null, values, true);
        }

        return locationId;
    }

    /**
     * Method for parsing the search results
     * 
     * @param response
     * @return
     * @throws JSONException If the Json resposne is malformed
     */
    private ResponseInfo parseSearchBooksResponse(final String response)
                    throws JSONException {

        final ResponseInfo responseInfo = new ResponseInfo();

        final JSONObject responseObject = new JSONObject(response);
        final JSONArray searchResults = JsonUtils
                        .readJSONArray(responseObject, HttpConstants.SEARCH, true, true);

        JSONObject bookObject = null;
        final ContentValues values = new ContentValues();
        final String selection = DatabaseColumns.BOOK_ID
                        + SQLConstants.EQUALS_ARG;
        final String[] args = new String[1];
        for (int i = 0; i < searchResults.length(); i++) {
            bookObject = JsonUtils
                            .readJSONObject(searchResults, i, false, false);
            args[0] = readBookDetailsIntoContentValues(bookObject, values, true);

            //First try to update the table if a book already exists
            if (DBInterface.update(TableSearchBooks.NAME, values, selection, args, true) == 0) {

                // Unable to update, insert the item
                DBInterface.insert(TableSearchBooks.NAME, null, values, true);
            }
        }
        return responseInfo;
    }

    /**
     * Method for parsing the hangouts response
     * 
     * @param response The Json string response
     * @return
     * @throws JSONException if the Json string is invalid
     */
    private ResponseInfo parseHangoutsResponse(final String response)
                    throws JSONException {

        final ResponseInfo responseInfo = new ResponseInfo();

        final JSONObject responseObject = new JSONObject(response);
        final JSONArray hangoutsArray = JsonUtils
                        .readJSONArray(responseObject, HttpConstants.LOCATIONS, true, true);

        final Hangout[] hangouts = new Hangout[hangoutsArray.length()];
        JSONObject hangoutObject = null;
        for (int i = 0; i < hangouts.length; i++) {
            hangoutObject = JsonUtils
                            .readJSONObject(hangoutsArray, i, true, true);
            hangouts[i] = new Hangout();
            readHangoutObjectIntoHangout(hangoutObject, hangouts[i]);
        }

        final Bundle responseBundle = new Bundle(1);
        responseBundle.putParcelableArray(HttpConstants.LOCATIONS, hangouts);
        responseInfo.responseBundle = responseBundle;
        return responseInfo;
    }

    /**
     * Parse the set user preferred location response
     * 
     * @param response The Json response representing the set location
     * @return
     * @throws JSONException If response is invalid json
     */
    private ResponseInfo parseSetUserPreferredLocationResponse(String response)
                    throws JSONException {
        final ResponseInfo responseInfo = new ResponseInfo();

        final JSONObject responseObject = new JSONObject(response);
        final JSONObject locationObject = JsonUtils
                        .readJSONObject(responseObject, HttpConstants.LOCATION, true, true);

        final String locationId = parseAndStoreLocation(locationObject);
        final Bundle responseBundle = new Bundle(1);
        responseBundle.putString(HttpConstants.LOCATION, locationId);
        responseInfo.responseBundle = responseBundle;
        return responseInfo;
    }

    /**
     * Reads a Hangout {@link JSONObject} into a {@link Hangout} model
     * 
     * @param hangoutObject The Json response representing a Hangout
     * @param hangout The {@link Hangout} model to write into
     * @throws JSONException If the Json is invalid
     */
    private void readHangoutObjectIntoHangout(final JSONObject hangoutObject,
                    final Hangout hangout) throws JSONException {

        hangout.name = JsonUtils
                        .readString(hangoutObject, HttpConstants.NAME, true, true);
        hangout.address = JsonUtils
                        .readString(hangoutObject, HttpConstants.ADDRESS, true, true);
        hangout.latitude = JsonUtils
                        .readDouble(hangoutObject, HttpConstants.LATITUDE, true, true);
        hangout.longitude = JsonUtils
                        .readDouble(hangoutObject, HttpConstants.LONGITUDE, true, true);

    }

    /**
     * Reads the book details from the Book response json into a content values
     * object
     * 
     * @param bookObject The Json representation of a book search result
     * @param values The values instance to read into
     * @param clearBeforeAdd Whether the values should be emptied before adding
     * @return The book Id that was parsed
     * @throws JSONException If the Json is invalid
     */
    private String readBookDetailsIntoContentValues(
                    final JSONObject bookObject, final ContentValues values,
                    final boolean clearBeforeAdd) throws JSONException {

        if (clearBeforeAdd) {
            values.clear();
        }

        final String bookId = JsonUtils
                        .readString(bookObject, HttpConstants.ID, true, true);

        values.put(DatabaseColumns.BOOK_ID, bookId);
        values.put(DatabaseColumns.ISBN_10, JsonUtils
                        .readString(bookObject, HttpConstants.ISBN_10, false, false));
        values.put(DatabaseColumns.ISBN_13, JsonUtils
                        .readString(bookObject, HttpConstants.ISBN_13, false, false));
        values.put(DatabaseColumns.AUTHOR, JsonUtils
                        .readString(bookObject, HttpConstants.AUTHOR, false, false));
        values.put(DatabaseColumns.BARTER_TYPE, JsonUtils
                        .readString(bookObject, HttpConstants.BARTER_TYPE, false, false));
        values.put(DatabaseColumns.USER_ID, JsonUtils
                        .readString(bookObject, HttpConstants.USER_ID, false, false));
        values.put(DatabaseColumns.TITLE, JsonUtils
                        .readString(bookObject, HttpConstants.TITLE, false, false));
        values.put(DatabaseColumns.DESCRIPTION, JsonUtils
                        .readString(bookObject, HttpConstants.DESCRIPTION, false, false));
        values.put(DatabaseColumns.IMAGE_URL, JsonUtils
                        .readString(bookObject, HttpConstants.IMAGE_URL, false, false));

        final JSONObject locationObject = JsonUtils
                        .readJSONObject(bookObject, HttpConstants.LOCATION, false, false);

        if (locationObject != null) {
            values.put(DatabaseColumns.LOCATION_ID, parseAndStoreLocation(locationObject));
        }
        return bookId;
    }

    /**
     * Reads the location details from the Location response json into a content
     * values object
     * 
     * @param locationObject The Json representation of a location
     * @param values The values instance to read into
     * @param clearBeforeAdd Whether the values should be emptied before adding
     * @return The location Id that was parsed
     * @throws JSONException If the Json is invalid
     */
    private String readLocationDetailsIntoContentValues(
                    final JSONObject locationObject,
                    final ContentValues values, final boolean clearBeforeAdd)
                    throws JSONException {

        if (clearBeforeAdd) {
            values.clear();
        }

        final String locationId = JsonUtils
                        .readString(locationObject, HttpConstants.ID, true, true);
        values.put(DatabaseColumns.LOCATION_ID, locationId);
        values.put(DatabaseColumns.NAME, JsonUtils
                        .readString(locationObject, HttpConstants.NAME, true, true));
        values.put(DatabaseColumns.ADDRESS, JsonUtils
                        .readString(locationObject, HttpConstants.ADDRESS, true, true));
        values.put(DatabaseColumns.LATITUDE, JsonUtils
                        .readDouble(locationObject, HttpConstants.LATITUDE, true, true));
        values.put(DatabaseColumns.LONGITUDE, JsonUtils
                        .readDouble(locationObject, HttpConstants.LONGITUDE, true, true));
        return locationId;
    }

    /**
     * @param response
     * @return
     */
    private ResponseInfo parseGetBookInfoResponse(final String response) {
        // TODO Parse get book info response
        return new ResponseInfo();
    }

    /**
     * @param response
     * @return
     */
    private ResponseInfo parseCreateBookResponse(final String response) {
        // TODO Parse get create book response
        return new ResponseInfo();
    }

    /**
     * Parses the string response(when the request was unsuccessful -
     * {@linkplain HttpStatus#SC_BAD_REQUEST} was returned) for a particular
     * {@linkplain RequestId} and returns the response data
     * 
     * @param requestId The {@linkplain RequestId} for the request
     * @param response The response from the server
     * @return a {@linkplain BlBadRequestError} object representing the response
     * @throws JSONException If the response was an invalid json
     */
    public BlBadRequestError getErrorResponse(final int requestId,
                    final String response) throws JSONException {

        Logger.d(TAG, "Request Id %d\nResponse %s", requestId, response);
        return parseErrorResponse(requestId, response);
    }

    /**
     * Do the actual parsing of error response here
     * 
     * @param requestId The {@linkplain RequestId} for the request
     * @param response The Json response from server
     * @return a {@link BlBadRequestError} object representing the error
     * @throws JSONException If the response was invalid json
     */
    private BlBadRequestError parseErrorResponse(final int requestId,
                    final String response) throws JSONException {

        final JSONObject errorObject = new JSONObject(response);

        final int errorCode = JsonUtils
                        .readInt(errorObject, HttpConstants.ERROR_CODE, true, true);
        final String errorMessage = JsonUtils
                        .readString(errorObject, HttpConstants.ERROR_MESSAGE, true, true);
        //Parse error response specific to any request here

        final BlBadRequestError error = new BlBadRequestError(errorCode, errorMessage);
        return error;

    }

}