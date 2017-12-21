package org.nypl.simplified.books.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.io7m.jfunctional.FunctionType;
import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.ProcedureType;
import com.io7m.jfunctional.Some;
import com.io7m.jnull.NullCheck;
import com.io7m.junreachable.UnreachableCodeException;

import org.nypl.drm.core.AdobeDeviceID;
import org.nypl.drm.core.AdobeUserID;
import org.nypl.drm.core.AdobeVendorID;
import org.nypl.simplified.books.accounts.AccountAuthenticationAdobeClientToken;
import org.nypl.simplified.books.accounts.AccountAuthenticationProvider;
import org.nypl.simplified.books.accounts.AccountBarcode;
import org.nypl.simplified.books.accounts.AccountPIN;
import org.nypl.simplified.books.accounts.AccountPatron;
import org.nypl.simplified.http.core.HTTPOAuthToken;
import org.nypl.simplified.json.core.JSONParseException;
import org.nypl.simplified.json.core.JSONParserUtilities;
import org.nypl.simplified.json.core.JSONSerializerUtilities;
import org.nypl.simplified.opds.core.DRMLicensor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Functions for serializing/deserializing account credentials.
 */

public final class AccountCredentialsJSON
{
  private AccountCredentialsJSON()
  {
    throw new UnreachableCodeException();
  }

  /**
   * Serialize the given credentials to a JSON object, and serialize that to a
   * UTF-8 string.
   *
   * @param credentials The credentials.
   *
   * @return A string of JSON
   *
   * @throws IOException On I/O or serialization errors
   */

  public static String serializeToText(final AccountCredentials credentials)
    throws IOException
  {
    NullCheck.notNull(credentials);

    final ObjectNode jo = AccountCredentialsJSON.serializeToJSON(credentials);
    final ByteArrayOutputStream bao = new ByteArrayOutputStream(1024);
    JSONSerializerUtilities.serialize(jo, bao);
    return bao.toString("UTF-8");
  }

  /**
   * Serialize the given credentials to a JSON object.
   *
   * @param credentials The credentials.
   *
   * @return A JSON object
   */

  public static ObjectNode serializeToJSON(final AccountCredentials credentials)
  {
    NullCheck.notNull(credentials);

    final ObjectMapper jom = new ObjectMapper();
    final ObjectNode jo = jom.createObjectNode();
    jo.put("username", credentials.getBarcode().toString());
    jo.put("password", credentials.getPin().toString());

    credentials.getProvider().map_(
      new ProcedureType<AccountAuthenticationProvider>()
      {
        @Override public void call(final AccountAuthenticationProvider x)
        {
          jo.put("provider", x.toString());
        }
      });

    credentials.getAdobeUserID().map_(
      new ProcedureType<AdobeUserID>()
      {
        @Override public void call(final AdobeUserID x)
        {
          jo.put("user_id", x.toString());
        }
      });
    credentials.getAdobeDeviceID().map_(
      new ProcedureType<AdobeDeviceID>()
      {
        @Override public void call(final AdobeDeviceID x)
        {
          jo.put("device_id", x.toString());
        }
      });
    credentials.getPatron().map_(
      new ProcedureType<AccountPatron>()
      {
        @Override public void call(final AccountPatron x)
        {
          jo.put("patron", x.toString());
        }
      });
    credentials.getOAuthToken().map_(
      new ProcedureType<HTTPOAuthToken>()
      {
        @Override public void call(final HTTPOAuthToken x)
        {
          jo.put("auth_token", x.toString());
        }
      });
    credentials.getAdobeToken().map_(
      new ProcedureType<AccountAuthenticationAdobeClientToken>()
      {
        @Override public void call(final AccountAuthenticationAdobeClientToken x)
        {
          jo.put("adobe_token", x.toString());
        }
      });

    if (credentials.getDrmLicensor().isSome()) {
      final DRMLicensor licensor = ((Some<DRMLicensor>) credentials.getDrmLicensor()).get();
      if (licensor.getDeviceManager().isSome()) {

        licensor.getDeviceManager().map_(
          new ProcedureType<String>() {
            @Override
            public void call(final String x) {
              jo.put("licensor_url", x);
            }
          });

      }
    }

    credentials.getAdobeVendor().map_(
      new ProcedureType<AdobeVendorID>()
      {
        @Override public void call(final AdobeVendorID x)
        {
          jo.put("adobe-vendor", x.toString());
        }
      });

    return jo;
  }

  /**
   * Deserialize the given text, which is assumed to be a JSON object
   * representing account credentials.
   *
   * @param text The credentials text.
   *
   * @return Account credentials
   *
   * @throws IOException On I/O or serialization errors
   */

  public static AccountCredentials deserializeFromText(final String text)
    throws IOException
  {
    NullCheck.notNull(text);
    final ObjectMapper jom = new ObjectMapper();
    return AccountCredentialsJSON.deserializeFromJSON(jom.readTree(text));
  }

  /**
   * Deserialize the given JSON node, which is assumed to be a JSON object
   * representing account credentials.
   *
   * @param node Credentials as a JSON node.
   *
   * @return Account credentials
   *
   * @throws JSONParseException On parse errors
   */

  public static AccountCredentials deserializeFromJSON(final JsonNode node)
    throws JSONParseException
  {
    NullCheck.notNull(node);
    final ObjectNode obj = JSONParserUtilities.checkObject(null, node);
    final AccountBarcode user =
      AccountBarcode.create(JSONParserUtilities.getString(obj, "username"));
    final AccountPIN pass =
      AccountPIN.create(JSONParserUtilities.getString(obj, "password"));
    
    final OptionType<AccountAuthenticationProvider> provider =
      JSONParserUtilities.getStringOptional(obj, "provider").map(
        new FunctionType<String, AccountAuthenticationProvider>()
        {
          @Override public AccountAuthenticationProvider call(final String x)
          {
            return AccountAuthenticationProvider.create(x);
          }
        });


    final OptionType<AccountPatron> patron =
      JSONParserUtilities.getStringOptional(obj, "patron").map(
      new FunctionType<String, AccountPatron>()
      {
        @Override public AccountPatron call(final String x)
        {
          return AccountPatron.create(x);
        }
      });
    final OptionType<HTTPOAuthToken> auth_token =
      JSONParserUtilities.getStringOptional(obj, "auth_token").map(
      new FunctionType<String, HTTPOAuthToken>()
      {
        @Override public HTTPOAuthToken call(final String x)
        {
          return HTTPOAuthToken.create(x);
        }
      });
    final OptionType<AccountAuthenticationAdobeClientToken> adobe_token =
      JSONParserUtilities.getStringOptional(obj, "adobe_token").map(
      new FunctionType<String, AccountAuthenticationAdobeClientToken>()
      {
        @Override public AccountAuthenticationAdobeClientToken call(final String x)
        {
          return AccountAuthenticationAdobeClientToken.create(x);
        }
      });

    final OptionType<AdobeUserID> adobe_user =
      JSONParserUtilities.getStringOptional(obj, "user_id").map(
      new FunctionType<String, AdobeUserID>()
      {
        @Override public AdobeUserID call(final String x)
        {
          return new AdobeUserID(x);
        }
      });
    final OptionType<AdobeDeviceID> adobe_device =
      JSONParserUtilities.getStringOptional(obj, "device_id").map(
      new FunctionType<String, AdobeDeviceID>()
      {
        @Override public AdobeDeviceID call(final String x)
        {
          return new AdobeDeviceID(x);
        }
      });

    final OptionType<AdobeVendorID> vendor =
      JSONParserUtilities.getStringOptional(obj, "adobe-vendor").map(
        new FunctionType<String, AdobeVendorID>()
        {
          @Override public AdobeVendorID call(final String x)
          {
            return new AdobeVendorID(x);
          }
        });

    final OptionType<String> licensor_url =
      JSONParserUtilities.getStringOptional(obj, "licensor_url").map(
        new FunctionType<String, String>()
        {
          @Override public String call(final String x)
          {
            return x;
          }
        });

    final AccountCredentials creds = new AccountCredentials(vendor, user, pass, provider, auth_token, adobe_token, patron);
    creds.setAdobeUserID(adobe_user);
    creds.setAdobeDeviceID(adobe_device);

    if (vendor.isSome() && adobe_token.isSome()) {

      final OptionType<DRMLicensor> licensor = Option.some(new DRMLicensor(
        ((Some<AdobeVendorID>) vendor).get().toString(),
        ((Some<AccountAuthenticationAdobeClientToken>) adobe_token).get().toString(),
        licensor_url));
      creds.setDrmLicensor(licensor);
    }

    return creds;
  }
}
