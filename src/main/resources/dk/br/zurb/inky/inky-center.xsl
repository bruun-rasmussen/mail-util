<?xml version="1.0" encoding="UTF-8"?>

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <xsl:template match="@*|node()">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="center">
    <xsl:apply-templates select="@*|node()"/>
  </xsl:template>

  <xsl:template match="*">
    <xsl:copy>
      <xsl:choose>
        <xsl:when test="parent::center">
          <xsl:attribute name="align">center</xsl:attribute>
          <xsl:attribute name="class">
            <xsl:if test="@class">
              <xsl:value-of select="concat(@class, ' ')" />
            </xsl:if>
            <xsl:value-of select="'float-center'" />
          </xsl:attribute>
          <xsl:apply-templates select="@*[name() != 'class']"/>
          <xsl:apply-templates />
        </xsl:when>
        <xsl:otherwise>
          <xsl:apply-templates select="@*|node()"/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:copy>
  </xsl:template>

</xsl:stylesheet>