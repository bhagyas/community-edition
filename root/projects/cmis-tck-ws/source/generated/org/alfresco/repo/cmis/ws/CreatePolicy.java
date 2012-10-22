/**
 * CreatePolicy.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package org.alfresco.repo.cmis.ws;

public class CreatePolicy  implements java.io.Serializable {
    private java.lang.String repositoryId;

    private org.alfresco.repo.cmis.ws.CmisPropertiesType properties;

    private java.lang.String folderId;

    private java.lang.String[] policies;

    private org.alfresco.repo.cmis.ws.CmisAccessControlListType addACEs;

    private org.alfresco.repo.cmis.ws.CmisAccessControlListType removeACEs;

    /* This is an extension element to hold any
     * 							repository or
     * 							vendor-specific extensions */
    private org.alfresco.repo.cmis.ws.CmisExtensionType extension;

    public CreatePolicy() {
    }

    public CreatePolicy(
           java.lang.String repositoryId,
           org.alfresco.repo.cmis.ws.CmisPropertiesType properties,
           java.lang.String folderId,
           java.lang.String[] policies,
           org.alfresco.repo.cmis.ws.CmisAccessControlListType addACEs,
           org.alfresco.repo.cmis.ws.CmisAccessControlListType removeACEs,
           org.alfresco.repo.cmis.ws.CmisExtensionType extension) {
           this.repositoryId = repositoryId;
           this.properties = properties;
           this.folderId = folderId;
           this.policies = policies;
           this.addACEs = addACEs;
           this.removeACEs = removeACEs;
           this.extension = extension;
    }


    /**
     * Gets the repositoryId value for this CreatePolicy.
     * 
     * @return repositoryId
     */
    public java.lang.String getRepositoryId() {
        return repositoryId;
    }


    /**
     * Sets the repositoryId value for this CreatePolicy.
     * 
     * @param repositoryId
     */
    public void setRepositoryId(java.lang.String repositoryId) {
        this.repositoryId = repositoryId;
    }


    /**
     * Gets the properties value for this CreatePolicy.
     * 
     * @return properties
     */
    public org.alfresco.repo.cmis.ws.CmisPropertiesType getProperties() {
        return properties;
    }


    /**
     * Sets the properties value for this CreatePolicy.
     * 
     * @param properties
     */
    public void setProperties(org.alfresco.repo.cmis.ws.CmisPropertiesType properties) {
        this.properties = properties;
    }


    /**
     * Gets the folderId value for this CreatePolicy.
     * 
     * @return folderId
     */
    public java.lang.String getFolderId() {
        return folderId;
    }


    /**
     * Sets the folderId value for this CreatePolicy.
     * 
     * @param folderId
     */
    public void setFolderId(java.lang.String folderId) {
        this.folderId = folderId;
    }


    /**
     * Gets the policies value for this CreatePolicy.
     * 
     * @return policies
     */
    public java.lang.String[] getPolicies() {
        return policies;
    }


    /**
     * Sets the policies value for this CreatePolicy.
     * 
     * @param policies
     */
    public void setPolicies(java.lang.String[] policies) {
        this.policies = policies;
    }

    public java.lang.String getPolicies(int i) {
        return this.policies[i];
    }

    public void setPolicies(int i, java.lang.String _value) {
        this.policies[i] = _value;
    }


    /**
     * Gets the addACEs value for this CreatePolicy.
     * 
     * @return addACEs
     */
    public org.alfresco.repo.cmis.ws.CmisAccessControlListType getAddACEs() {
        return addACEs;
    }


    /**
     * Sets the addACEs value for this CreatePolicy.
     * 
     * @param addACEs
     */
    public void setAddACEs(org.alfresco.repo.cmis.ws.CmisAccessControlListType addACEs) {
        this.addACEs = addACEs;
    }


    /**
     * Gets the removeACEs value for this CreatePolicy.
     * 
     * @return removeACEs
     */
    public org.alfresco.repo.cmis.ws.CmisAccessControlListType getRemoveACEs() {
        return removeACEs;
    }


    /**
     * Sets the removeACEs value for this CreatePolicy.
     * 
     * @param removeACEs
     */
    public void setRemoveACEs(org.alfresco.repo.cmis.ws.CmisAccessControlListType removeACEs) {
        this.removeACEs = removeACEs;
    }


    /**
     * Gets the extension value for this CreatePolicy.
     * 
     * @return extension   * This is an extension element to hold any
     * 							repository or
     * 							vendor-specific extensions
     */
    public org.alfresco.repo.cmis.ws.CmisExtensionType getExtension() {
        return extension;
    }


    /**
     * Sets the extension value for this CreatePolicy.
     * 
     * @param extension   * This is an extension element to hold any
     * 							repository or
     * 							vendor-specific extensions
     */
    public void setExtension(org.alfresco.repo.cmis.ws.CmisExtensionType extension) {
        this.extension = extension;
    }

    private java.lang.Object __equalsCalc = null;
    public synchronized boolean equals(java.lang.Object obj) {
        if (!(obj instanceof CreatePolicy)) return false;
        CreatePolicy other = (CreatePolicy) obj;
        if (obj == null) return false;
        if (this == obj) return true;
        if (__equalsCalc != null) {
            return (__equalsCalc == obj);
        }
        __equalsCalc = obj;
        boolean _equals;
        _equals = true && 
            ((this.repositoryId==null && other.getRepositoryId()==null) || 
             (this.repositoryId!=null &&
              this.repositoryId.equals(other.getRepositoryId()))) &&
            ((this.properties==null && other.getProperties()==null) || 
             (this.properties!=null &&
              this.properties.equals(other.getProperties()))) &&
            ((this.folderId==null && other.getFolderId()==null) || 
             (this.folderId!=null &&
              this.folderId.equals(other.getFolderId()))) &&
            ((this.policies==null && other.getPolicies()==null) || 
             (this.policies!=null &&
              java.util.Arrays.equals(this.policies, other.getPolicies()))) &&
            ((this.addACEs==null && other.getAddACEs()==null) || 
             (this.addACEs!=null &&
              this.addACEs.equals(other.getAddACEs()))) &&
            ((this.removeACEs==null && other.getRemoveACEs()==null) || 
             (this.removeACEs!=null &&
              this.removeACEs.equals(other.getRemoveACEs()))) &&
            ((this.extension==null && other.getExtension()==null) || 
             (this.extension!=null &&
              this.extension.equals(other.getExtension())));
        __equalsCalc = null;
        return _equals;
    }

    private boolean __hashCodeCalc = false;
    public synchronized int hashCode() {
        if (__hashCodeCalc) {
            return 0;
        }
        __hashCodeCalc = true;
        int _hashCode = 1;
        if (getRepositoryId() != null) {
            _hashCode += getRepositoryId().hashCode();
        }
        if (getProperties() != null) {
            _hashCode += getProperties().hashCode();
        }
        if (getFolderId() != null) {
            _hashCode += getFolderId().hashCode();
        }
        if (getPolicies() != null) {
            for (int i=0;
                 i<java.lang.reflect.Array.getLength(getPolicies());
                 i++) {
                java.lang.Object obj = java.lang.reflect.Array.get(getPolicies(), i);
                if (obj != null &&
                    !obj.getClass().isArray()) {
                    _hashCode += obj.hashCode();
                }
            }
        }
        if (getAddACEs() != null) {
            _hashCode += getAddACEs().hashCode();
        }
        if (getRemoveACEs() != null) {
            _hashCode += getRemoveACEs().hashCode();
        }
        if (getExtension() != null) {
            _hashCode += getExtension().hashCode();
        }
        __hashCodeCalc = false;
        return _hashCode;
    }

    // Type metadata
    private static org.apache.axis.description.TypeDesc typeDesc =
        new org.apache.axis.description.TypeDesc(CreatePolicy.class, true);

    static {
        typeDesc.setXmlType(new javax.xml.namespace.QName("http://docs.oasis-open.org/ns/cmis/messaging/200908/", ">createPolicy"));
        org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("repositoryId");
        elemField.setXmlName(new javax.xml.namespace.QName("http://docs.oasis-open.org/ns/cmis/messaging/200908/", "repositoryId"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("properties");
        elemField.setXmlName(new javax.xml.namespace.QName("http://docs.oasis-open.org/ns/cmis/messaging/200908/", "properties"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://docs.oasis-open.org/ns/cmis/core/200908/", "cmisPropertiesType"));
        elemField.setNillable(false);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("folderId");
        elemField.setXmlName(new javax.xml.namespace.QName("http://docs.oasis-open.org/ns/cmis/messaging/200908/", "folderId"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setMinOccurs(0);
        elemField.setNillable(true);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("policies");
        elemField.setXmlName(new javax.xml.namespace.QName("http://docs.oasis-open.org/ns/cmis/messaging/200908/", "policies"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        elemField.setMinOccurs(0);
        elemField.setNillable(true);
        elemField.setMaxOccursUnbounded(true);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("addACEs");
        elemField.setXmlName(new javax.xml.namespace.QName("http://docs.oasis-open.org/ns/cmis/messaging/200908/", "addACEs"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://docs.oasis-open.org/ns/cmis/core/200908/", "cmisAccessControlListType"));
        elemField.setMinOccurs(0);
        elemField.setNillable(true);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("removeACEs");
        elemField.setXmlName(new javax.xml.namespace.QName("http://docs.oasis-open.org/ns/cmis/messaging/200908/", "removeACEs"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://docs.oasis-open.org/ns/cmis/core/200908/", "cmisAccessControlListType"));
        elemField.setMinOccurs(0);
        elemField.setNillable(true);
        typeDesc.addFieldDesc(elemField);
        elemField = new org.apache.axis.description.ElementDesc();
        elemField.setFieldName("extension");
        elemField.setXmlName(new javax.xml.namespace.QName("http://docs.oasis-open.org/ns/cmis/messaging/200908/", "extension"));
        elemField.setXmlType(new javax.xml.namespace.QName("http://docs.oasis-open.org/ns/cmis/messaging/200908/", "cmisExtensionType"));
        elemField.setMinOccurs(0);
        elemField.setNillable(true);
        typeDesc.addFieldDesc(elemField);
    }

    /**
     * Return type metadata object
     */
    public static org.apache.axis.description.TypeDesc getTypeDesc() {
        return typeDesc;
    }

    /**
     * Get Custom Serializer
     */
    public static org.apache.axis.encoding.Serializer getSerializer(
           java.lang.String mechType, 
           java.lang.Class _javaType,  
           javax.xml.namespace.QName _xmlType) {
        return 
          new  org.apache.axis.encoding.ser.BeanSerializer(
            _javaType, _xmlType, typeDesc);
    }

    /**
     * Get Custom Deserializer
     */
    public static org.apache.axis.encoding.Deserializer getDeserializer(
           java.lang.String mechType, 
           java.lang.Class _javaType,  
           javax.xml.namespace.QName _xmlType) {
        return 
          new  org.apache.axis.encoding.ser.BeanDeserializer(
            _javaType, _xmlType, typeDesc);
    }

}
