package local.akenori.receiver;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.Bundle;
import android.graphics.Rect;
import android.util.Xml;
import android.view.accessibility.AccessibilityNodeInfo;
import org.xmlpull.v1.XmlSerializer;
import java.io.StringWriter;

/** Test-only UI dump preserving the receiver service, unlike shell uiautomator dump. */
public class DumpInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            UiAutomation automation=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            Thread.sleep(500);
            StringWriter writer=new StringWriter(); XmlSerializer xml=Xml.newSerializer(); xml.setOutput(writer); xml.startTag(null,"hierarchy");
            AccessibilityNodeInfo root=automation.getRootInActiveWindow(); if(root!=null) dump(xml,root);
            xml.endTag(null,"hierarchy"); xml.flush(); result.putString("hierarchy",writer.toString()); finish(0,result);
        } catch(Exception e) { result.putString("error",e.toString()); finish(1,result); }
    }
    private void dump(XmlSerializer xml,AccessibilityNodeInfo n) throws Exception {
        xml.startTag(null,"node"); Rect b=new Rect(); n.getBoundsInScreen(b);
        xml.attribute(null,"text",n.getText()==null?"":n.getText().toString()); xml.attribute(null,"class",String.valueOf(n.getClassName()));
        xml.attribute(null,"bounds","["+b.left+","+b.top+"]["+b.right+","+b.bottom+"]"); xml.attribute(null,"enabled",String.valueOf(n.isEnabled()));
        xml.attribute(null,"focused",String.valueOf(n.isFocused())); xml.attribute(null,"selected",String.valueOf(n.isSelected()));
        for(int i=0;i<n.getChildCount();i++) { AccessibilityNodeInfo c=n.getChild(i); if(c!=null) dump(xml,c); }
        xml.endTag(null,"node"); n.recycle();
    }
}
