package net.java.dev.webdav.core.jaxrs.xml.properties;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;

/**
 * Microsoft Exchange Server 2003 item. http://msdn.microsoft.com/en-us/library/aa487551(v=EXCHG.65).aspx
 *
 * @author Organization: Gagnavarslan ehf
 */
@XmlRootElement(name = "isFolder")
public final class IsHidden {

    @XmlValue
	private Integer hidden;

    public IsHidden() {
        
    }

    public IsHidden(Integer hidden) {
        this.hidden = hidden;
    }

    public Integer getHidden() {
        return hidden;
    }
}
