/***** BEGIN LICENSE BLOCK *****
 * Version: CPL 1.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Common Public
 * License Version 1.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/cpl-v10.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Copyright (C) 2006 Ola Bini <ola@ologix.com>
 * 
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the CPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the CPL, the GPL or the LGPL.
 ***** END LICENSE BLOCK *****/
package org.jruby.openssl;

import java.io.StringReader;
import java.io.StringWriter;

import java.math.BigInteger;

import java.security.KeyPair;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.security.spec.RSAKeyGenParameterSpec;

import javax.crypto.Cipher;

import org.jruby.IRuby;
import org.jruby.RubyClass;
import org.jruby.RubyFixnum;
import org.jruby.RubyModule;
import org.jruby.RubyObject;
import org.jruby.RubyNumeric;

import org.jruby.exceptions.RaiseException;
import org.jruby.runtime.CallbackFactory;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * @author <a href="mailto:ola.bini@ki.se">Ola Bini</a>
 */
public class PKeyRSA extends PKey {
    public static void createPKeyRSA(IRuby runtime, RubyModule mPKey) {
        RubyClass cRSA = mPKey.defineClassUnder("RSA",mPKey.getClass("PKey"));
        mPKey.defineClassUnder("RSAError",mPKey.getClass("PKeyError"));
        
        CallbackFactory rsacb = runtime.callbackFactory(PKeyRSA.class);

        cRSA.defineSingletonMethod("new",rsacb.getOptSingletonMethod("newInstance"));
        cRSA.defineMethod("initialize",rsacb.getOptMethod("initialize"));
        cRSA.defineMethod("public?",rsacb.getMethod("public_p"));
        cRSA.defineMethod("private?",rsacb.getMethod("private_p"));
        cRSA.defineMethod("to_der",rsacb.getMethod("to_der"));
        cRSA.defineMethod("public_key",rsacb.getMethod("public_key"));
        cRSA.defineMethod("export",rsacb.getOptMethod("export"));
        cRSA.defineMethod("to_pem",rsacb.getOptMethod("export"));
        cRSA.defineMethod("to_s",rsacb.getOptMethod("export"));
        cRSA.defineMethod("private_encrypt",rsacb.getOptMethod("private_encrypt"));
        cRSA.defineMethod("private_decrypt",rsacb.getOptMethod("private_decrypt"));
        cRSA.defineMethod("public_encrypt",rsacb.getOptMethod("public_encrypt"));
        cRSA.defineMethod("public_decrypt",rsacb.getOptMethod("public_decrypt"));

        cRSA.setConstant("PKCS1_PADDING",runtime.newFixnum(1));
        cRSA.setConstant("SSLV23_PADDING",runtime.newFixnum(2));
        cRSA.setConstant("NO_PADDING",runtime.newFixnum(3));
        cRSA.setConstant("PKCS1_OAEP_PADDING",runtime.newFixnum(4));
   }

    public static IRubyObject newInstance(IRubyObject recv, IRubyObject[] args) {
        PKeyRSA result = new PKeyRSA(recv.getRuntime(), (RubyClass)recv);
        result.callInit(args);
        return result;
    }

    public PKeyRSA(IRuby runtime, RubyClass type) {
        super(runtime,type);
    }

    private RSAPrivateCrtKey privKey;
    private RSAPublicKey pubKey;

    public IRubyObject initialize(IRubyObject[] args) {
        Object rsa;
        IRubyObject arg;
        IRubyObject pass = null;
        String passwd = null;
        if(checkArgumentCount(args,0,2) == 0) {
            rsa = null; //RSA.new
        } else {
            arg = args[0];
            if(args.length > 1) {
                pass = args[1];
            }
            if(arg instanceof RubyFixnum) {
                int keyLen = RubyNumeric.fix2int(arg);
                BigInteger pubExp = RSAKeyGenParameterSpec.F4;
                if(null != pass && !pass.isNil()) {
                    pubExp = BigInteger.valueOf(RubyNumeric.num2long(pass));
                }
                try {
                    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
                    gen.initialize(new RSAKeyGenParameterSpec(keyLen,pubExp));
                    KeyPair pair = gen.generateKeyPair();
                    privKey = (RSAPrivateCrtKey)(pair.getPrivate());
                    pubKey = (RSAPublicKey)(pair.getPublic());
                } catch(Exception e) {
                    throw new RaiseException(getRuntime(), (RubyClass)(((RubyModule)(getRuntime().getModule("OpenSSL").getConstant("PKey"))).getConstant("RSAError")), null, true);
                }
            } else {
                if(pass != null && !pass.isNil()) {
                    passwd = pass.toString();
                }
                String input = arg.toString();

                Object val = null;
                KeyFactory fact = null;
                try {
                    fact = KeyFactory.getInstance("RSA");
                } catch(Exception e) {
                    throw getRuntime().newLoadError("unsupported key algorithm (RSA)");
                }

                if(null == val) {
                    try {
                        val = fact.generatePublic(new X509EncodedKeySpec(input.getBytes("PLAIN")));
                    } catch(Exception e) {
                        val = null;
                    }
                }
                if(null == val) {
                    try {
                        val = fact.generatePrivate(new PKCS8EncodedKeySpec(input.getBytes("PLAIN")));
                    } catch(Exception e) {
                        val = null;
                    }
                }
                if(null == val) {
                    try {
                        val = OpenSSLImpl.getPEMHandler().readPEM(new StringReader(input),passwd);
                    } catch(Exception e3) {
                        val = null;
                    }
                }
                if(null == val) {
                    throw new RaiseException(getRuntime(), (RubyClass)(((RubyModule)(getRuntime().getModule("OpenSSL").getConstant("PKey"))).getConstant("RSAError")), "Neither PUB key nor PRIV key:", true);
                }

                if(val instanceof KeyPair) {
                    privKey = (RSAPrivateCrtKey)(((KeyPair)val).getPrivate());
                    pubKey = (RSAPublicKey)(((KeyPair)val).getPublic());
                } else if(val instanceof RSAPrivateCrtKey) {
                    privKey = (RSAPrivateCrtKey)val;
                    try {
                        pubKey = (RSAPublicKey)(fact.generatePublic(new RSAPublicKeySpec(privKey.getModulus(),privKey.getPublicExponent())));
                    } catch(Exception e) {
                        throw new RaiseException(getRuntime(), (RubyClass)(((RubyModule)(getRuntime().getModule("OpenSSL").getConstant("PKey"))).getConstant("RSAError")), "Something rotten with private key", true);
                    }
                } else if(val instanceof RSAPublicKey) {
                    pubKey = (RSAPublicKey)val;
                    privKey = null;
                } else {
                    throw new RaiseException(getRuntime(), (RubyClass)(((RubyModule)(getRuntime().getModule("OpenSSL").getConstant("PKey"))).getConstant("RSAError")), "Neither PUB key nor PRIV key:", true);
                }

            }
        }

        return this;
    }

    public IRubyObject public_p() {
        return pubKey != null ? getRuntime().getTrue() : getRuntime().getFalse();
    }

    public IRubyObject private_p() {
        return privKey != null ? getRuntime().getTrue() : getRuntime().getFalse();
    }

    public IRubyObject to_der() throws Exception {
        return getRuntime().newString( new String(privKey == null ? pubKey.getEncoded() : privKey.getEncoded(),"ISO8859_1"));
    }

    public IRubyObject public_key() {
        PKeyRSA val = new PKeyRSA(getRuntime(),getMetaClass().getRealClass());
        val.privKey = null;
        val.pubKey = this.pubKey;
        return val;
    }

    public IRubyObject export(IRubyObject[] args) throws Exception {
        StringWriter w = new StringWriter();
        Object exp = pubKey;
        if(privKey != null) {
            exp = privKey;
        }
        if(args.length == 0) {
            OpenSSLImpl.getPEMHandler().writePEM(w,exp);
        } else {
            String algo = ((Cipher)args[0]).getAlgorithm();
            char[] passwd = null;
            if(args.length > 1 && !args[1].isNil()) {
                passwd = args[1].toString().toCharArray();
            }
            OpenSSLImpl.getPEMHandler().writePEM(w,exp,algo,passwd);
        }
        w.close();
        return getRuntime().newString(w.toString());
    }

    private String getPadding(int padding) {
        if(padding < 1 || padding > 4) {
            throw new RaiseException(getRuntime(), (RubyClass)(((RubyModule)(getRuntime().getModule("OpenSSL").getConstant("PKey"))).getConstant("RSAError")), null, true);
        }

        String p = "/NONE/PKCS1Padding";
        if(padding == 3) {
            p = "/NONE/NoPadding";
        } else if(padding == 4) {
            p = "/NONE/OAEPWithMD5AndMGF1Padding";
        } else if(padding == 2) {
            p = "/NONE/ISO9796-1Padding";
        }
        return p;
    }        

    public IRubyObject private_encrypt(IRubyObject[] args) throws Exception {
        int padding = 1;
        if(checkArgumentCount(args,1,2) == 2 && !args[1].isNil()) {
            padding = RubyNumeric.fix2int(args[1]);
        }
        String p = getPadding(padding);

        String buffer = args[0].toString();
        if(privKey == null) {
            throw new RaiseException(getRuntime(), (RubyClass)(((RubyModule)(getRuntime().getModule("OpenSSL").getConstant("PKey"))).getConstant("RSAError")), "private key needed.", true);
        }

        Cipher engine = Cipher.getInstance("RSA"+p);
        engine.init(Cipher.ENCRYPT_MODE,privKey);
        byte[] outp = engine.doFinal(buffer.getBytes("PLAIN"));
        return getRuntime().newString(new String(outp,"ISO8859_1"));
    }

    public IRubyObject private_decrypt(IRubyObject[] args) throws Exception {
        int padding = 1;
        if(checkArgumentCount(args,1,2) == 2 && !args[1].isNil()) {
            padding = RubyNumeric.fix2int(args[1]);
        }
        String p = getPadding(padding);

        String buffer = args[0].toString();
        if(privKey == null) {
            throw new RaiseException(getRuntime(), (RubyClass)(((RubyModule)(getRuntime().getModule("OpenSSL").getConstant("PKey"))).getConstant("RSAError")), "private key needed.", true);
        }

        Cipher engine = Cipher.getInstance("RSA"+p);
        engine.init(Cipher.DECRYPT_MODE,privKey);
        byte[] outp = engine.doFinal(buffer.getBytes("PLAIN"));
        return getRuntime().newString(new String(outp,"ISO8859_1"));
    }

    public IRubyObject public_encrypt(IRubyObject[] args) throws Exception {
        int padding = 1;
        if(checkArgumentCount(args,1,2) == 2 && !args[1].isNil()) {
            padding = RubyNumeric.fix2int(args[1]);
        }
        String p = getPadding(padding);

        String buffer = args[0].toString();
        Cipher engine = Cipher.getInstance("RSA"+p);
        engine.init(Cipher.ENCRYPT_MODE,pubKey);
        byte[] outp = engine.doFinal(buffer.getBytes("PLAIN"));
        return getRuntime().newString(new String(outp,"ISO8859_1"));
    }

    public IRubyObject public_decrypt(IRubyObject[] args) throws Exception {
        int padding = 1;
        if(checkArgumentCount(args,1,2) == 2 && !args[1].isNil()) {
            padding = RubyNumeric.fix2int(args[1]);
        }
        String p = getPadding(padding);

        String buffer = args[0].toString();
        Cipher engine = Cipher.getInstance("RSA"+p);
        engine.init(Cipher.DECRYPT_MODE,pubKey);
        byte[] outp = engine.doFinal(buffer.getBytes("PLAIN"));
        return getRuntime().newString(new String(outp,"ISO8859_1"));
    }
}// PKeyRSA
