package dev.terminalagent.client;

import com.sun.jna.*;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

final class WindowsCredentials {
 @Structure.FieldOrder({"flags","type","targetName","comment","lastWritten","credentialBlobSize","credentialBlob","persist","attributeCount","attributes","targetAlias","userName"})
 public static class Credential extends Structure {
  public int flags, type; public WString targetName, comment; public long lastWritten; public int credentialBlobSize; public Pointer credentialBlob; public int persist, attributeCount; public Pointer attributes; public WString targetAlias, userName;
  public Credential() {} public Credential(Pointer p) { super(p); read(); }
 }
 interface API extends StdCallLibrary {
  boolean CredWriteW(Credential credential, int flags);
  boolean CredReadW(WString target, int type, int flags, PointerByReference credential);
  boolean CredDeleteW(WString target, int type, int flags);
  void CredFree(Pointer buffer);
 }
 private static API api() { return Native.load("Advapi32", API.class, W32APIOptions.UNICODE_OPTIONS); }
 private static WString target(String server) throws Exception { byte[] hash=MessageDigest.getInstance("SHA-256").digest(server.getBytes(StandardCharsets.UTF_8)); return new WString("TerminalAgent/"+HexFormat.of().formatHex(hash)); }
 static String read(String server) throws Exception { PointerByReference out = new PointerByReference(); API api=api(); if(!api.CredReadW(target(server),1,0,out)) { int error=Native.getLastError(); if(error==1168) return null; throw new IllegalStateException("Credential read failed: "+error); } try { Credential c=new Credential(out.getValue()); return new String(c.credentialBlob.getByteArray(0,c.credentialBlobSize),StandardCharsets.UTF_8); } finally { api.CredFree(out.getValue()); } }
 static void write(String server,String token) throws Exception { byte[] bytes=token.getBytes(StandardCharsets.UTF_8); Memory mem=new Memory(bytes.length); mem.write(0,bytes,0,bytes.length); Credential c=new Credential(); c.type=1; c.targetName=target(server); c.credentialBlobSize=bytes.length; c.credentialBlob=mem; c.persist=2; c.userName=new WString("terminal-agent"); if(!api().CredWriteW(c,0)) throw new IllegalStateException("Credential save failed: "+Native.getLastError()); mem.clear(); java.util.Arrays.fill(bytes,(byte)0); }
 static void delete(String server) throws Exception { if(!api().CredDeleteW(target(server),1,0)) {int error=Native.getLastError(); if(error!=1168) throw new IllegalStateException("Credential delete failed: "+error); } }
}
