package dev.kaankaya.instagramtracker

import java.net.URLEncoder
import org.json.JSONObject

internal object SyntheticWebLogin {
    fun body(doc: String = WebLoginContract.DOCUMENT): String {
        val cipher="#PWD_BROWSER:5:1790000000:"+"A".repeat(96)
        val input=JSONObject().put("actor_id","0").put("identifier","synthetic.account").put("app","instagram")
            .put("credential_type","password").put("use_uid_to_login",false).put("passkey_payload",JSONObject.NULL)
            .put("password",JSONObject().put("sensitive_string_value",cipher)).put("enc_password",JSONObject().put("sensitive_string_value",cipher))
        return "doc_id=$doc&fb_api_req_friendly_name=${WebLoginContract.OPERATION}&variables="+
            URLEncoder.encode(JSONObject().put("input",input).put("scale",1).toString(),"UTF-8")
    }
}
