package pl.konczak.nzoz.ewus.domain.checkcwu.response;

import lombok.Getter;
import lombok.Setter;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

@Getter
@Setter
@XmlAccessorType(XmlAccessType.FIELD)
public class Signature {

    @XmlElement(name = "SignedInfo")
    private SignedInfo signedInfo;

    @XmlElement(name = "SignatureValue")
    private String signatureValue;

}
