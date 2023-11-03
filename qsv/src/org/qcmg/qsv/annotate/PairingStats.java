/**
 * © Copyright The University of Queensland 2010-2014.  This code is released under the terms outlined in the included LICENSE file.
 */
//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, vhudson-jaxb-ri-2.2-147 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2010.10.05 at 09:43:33 AM EST 
//


package org.qcmg.qsv.annotate;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlSchemaType;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for anonymous complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType>
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="InsertRange">
 *           &lt;complexType>
 *             &lt;complexContent>
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *                 &lt;attribute name="LowerLimit" use="required" type="{http://www.w3.org/2001/XMLSchema}positiveInteger" />
 *                 &lt;attribute name="UpperLimit" use="required" type="{http://www.w3.org/2001/XMLSchema}positiveInteger" />
 *               &lt;/restriction>
 *             &lt;/complexContent>
 *           &lt;/complexType>
 *         &lt;/element>
 *         &lt;element name="UniquePairing" maxOccurs="unbounded">
 *           &lt;complexType>
 *             &lt;complexContent>
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *                 &lt;sequence>
 *                   &lt;element name="type" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *                   &lt;element name="count" type="{http://www.w3.org/2001/XMLSchema}positiveInteger"/>
 *                 &lt;/sequence>
 *               &lt;/restriction>
 *             &lt;/complexContent>
 *           &lt;/complexType>
 *         &lt;/element>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "insertRange",
    "uniquePairing",
    "readCount",
    "coverage"
})
@XmlRootElement(name = "LongMatePairReport")
public class PairingStats {

    @XmlElement(name = "InsertRange", required = true)
    protected PairingStats.InsertRange insertRange;
    @XmlElement(name = "UniquePairing", required = true)
    protected List<PairingStats.UniquePairing> uniquePairing;
    @XmlElement(name = "ReadCount", required = true)
    protected List<PairingStats.ReadCount> readCount;
    @XmlElement(name = "Coverage", required = false)
    protected List<PairingStats.Coverage> coverage;
    
    /**
     * Gets the value of the insertRange property.
     * 
     * @return
     *     possible object is
     *     {@link PairingStats.InsertRange }
     *     
     */
    public PairingStats.InsertRange getInsertRange() {
        return insertRange;
    }

    /**
     * Sets the value of the insertRange property.
     * 
     * @param value
     *     allowed object is
     *     {@link PairingStats.InsertRange }
     *     
     */
    public void setInsertRange(PairingStats.InsertRange value) {
        this.insertRange = value;
    }

    /**
     * Gets the value of the uniquePairing property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the uniquePairing property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getUniquePairing().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PairingStats.UniquePairing }
     * 
     * 
     */
    public List<PairingStats.UniquePairing> getUniquePairing() {
        if (uniquePairing == null) {
            uniquePairing = new ArrayList<>();
        }
        return this.uniquePairing;
    }
    
    public List<PairingStats.ReadCount> getReadCount() {
        if (readCount == null) {
            readCount = new ArrayList<>();
        }
        return this.readCount;
    }
    
    public List<PairingStats.Coverage> getCoverage() {
        if (coverage == null) {
            coverage = new ArrayList<>();
        }
        return this.coverage;
    }
    
    /**
     * <p>Java class for anonymous complex type.
     * 
     * <p>The following schema fragment specifies the expected content contained within this class.
     * 
     * <pre>
     * &lt;complexType>
     *   &lt;complexContent>
     *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
     *       &lt;attribute name="LowerLimit" use="required" type="{http://www.w3.org/2001/XMLSchema}positiveInteger" />
     *       &lt;attribute name="UpperLimit" use="required" type="{http://www.w3.org/2001/XMLSchema}positiveInteger" />
     *     &lt;/restriction>
     *   &lt;/complexContent>
     * &lt;/complexType>
     * </pre>
     * 
     * 
     */
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "")
    public static class InsertRange {

        @XmlAttribute(name = "LowerLimit", required = true)
        @XmlSchemaType(name = "positiveInteger")
        protected BigInteger lowerLimit;
        @XmlAttribute(name = "UpperLimit", required = true)
        @XmlSchemaType(name = "positiveInteger")
        protected BigInteger upperLimit;
        @XmlAttribute(name = "Average", required = true)
        @XmlSchemaType(name = "positiveInteger")
        protected BigInteger average;

        /**
         * Gets the value of the lowerLimit property.
         *
         * @return
         *     possible object is
         *     {@link BigInteger }
         *
         */
        public BigInteger getLowerLimit() {
            return lowerLimit;
        }

        /**
         * Sets the value of the lowerLimit property.
         * 
         * @param value
         *     allowed object is
         *     {@link BigInteger }
         *     
         */
        public void setLowerLimit(BigInteger value) {
            this.lowerLimit = value;
        }

        /**
         * Gets the value of the upperLimit property.
         *
         * @return
         *     possible object is
         *     {@link BigInteger }
         *
         */
        public BigInteger getUpperLimit() {
            return upperLimit;
        }

        /**
         * Sets the value of the upperLimit property.
         * 
         * @param value
         *     allowed object is
         *     {@link BigInteger }
         *     
         */
        public void setUpperLimit(BigInteger value) {
            this.upperLimit = value;
        }


        public BigInteger getAverage() {
            return average;
        }

        public void setAverage(BigInteger value) {
            this.average = value;
        }

    }


    /**
     * <p>Java class for anonymous complex type.
     * 
     * <p>The following schema fragment specifies the expected content contained within this class.
     * 
     * <pre>
     * &lt;complexType>
     *   &lt;complexContent>
     *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
     *       &lt;sequence>
     *         &lt;element name="type" type="{http://www.w3.org/2001/XMLSchema}string"/>
     *         &lt;element name="count" type="{http://www.w3.org/2001/XMLSchema}positiveInteger"/>
     *       &lt;/sequence>
     *     &lt;/restriction>
     *   &lt;/complexContent>
     * &lt;/complexType>
     * </pre>
     * 
     * 
     */
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "", propOrder = {
        "type",
        "count"
    })
    public static class UniquePairing {

        @XmlElement(required = true)
        protected String type;
        @XmlElement(required = true)
        @XmlSchemaType(name = "positiveInteger")
        protected BigInteger count;

        /**
         * Gets the value of the type property.
         * 
         * @return
         *     possible object is
         *     {@link String }
         *     
         */
        public String getType() {
            return type;
        }

        /**
         * Sets the value of the type property.
         * 
         * @param value
         *     allowed object is
         *     {@link String }
         *     
         */
        public void setType(String value) {
            this.type = value;
        }

        /**
         * Gets the value of the count property.
         * 
         * @return
         *     possible object is
         *     {@link BigInteger }
         *     
         */
        public BigInteger getCount() {
            return count;
        }

        /**
         * Sets the value of the count property.
         * 
         * @param value
         *     allowed object is
         *     {@link BigInteger }
         *     
         */
        public void setCount(BigInteger value) {
            this.count = value;
        }

    }
    
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "", propOrder = {
        "type",
        "count"
    })
    
    public static class ReadCount {

        @XmlElement(required = true)
        protected String type;
        @XmlElement(required = true)
        @XmlSchemaType(name = "long")
        protected long count;

        /**
         * Gets the value of the type property.
         * 
         * @return
         *     possible object is
         *     {@link String }
         *     
         */
        public String getType() {
            return type;
        }

        /**
         * Sets the value of the type property.
         * 
         * @param value
         *     allowed object is
         *     {@link String }
         *     
         */
        public void setType(String value) {
            this.type = value;
        }

        /**
         * Gets the value of the count property.
         * 
         * @return
         *     possible object is
         *     {@link BigInteger }
         *     
         */
        public long getCount() {
            return count;
        }

        /**
         * Sets the value of the count property.
         * 
         * @param l
         *     allowed object is
         *     {@link BigInteger }
         *     
         */
        public void setCount(long l) {
            this.count = l;
        }

    }
    
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "", propOrder = {
        "type",
        "value"
    })
    public static class Coverage {

        @XmlElement(required = true)
        protected String type;
        @XmlElement(required = true)
        @XmlSchemaType(name = "float")
        protected String value;

        /**
         * Gets the value of the type property.
         * 
         * @return
         *     possible object is
         *     {@link String }
         *     
         */
        public String getType() {
            return type;
        }

        /**
         * Sets the value of the type property.
         * 
         * @param value
         *     allowed object is
         *     {@link String }
         *     
         */
        public void setType(String value) {
            this.type = value;
        }

        /**
         * Sets the value of the value property.
         * 
         * @param value
         *     allowed object is
         *     {@link String }
         *     
         */
        public void setValue(String value) {
            this.value = value;
        }

    }


}
