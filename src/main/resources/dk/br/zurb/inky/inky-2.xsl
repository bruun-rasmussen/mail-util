<?xml version="1.0" encoding="UTF-8"?>

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <xsl:template match="@*|node()|center">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()" />
    </xsl:copy>
  </xsl:template>

  <!-- Strip marker elements: -->
  <xsl:template match="noinky">
    <xsl:comment> [noinky] </xsl:comment>
      <xsl:apply-templates select="@*|node()" />
    <xsl:comment> [/noinky] </xsl:comment>
  </xsl:template>

  <!-- Strip inky attributes: -->
  <xsl:template match="@size|@size-sm|@size-lg|@small|@large" />

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
          <xsl:apply-templates select="@*[name() != 'class']|node()" />
        </xsl:when>
        <xsl:otherwise>
          <xsl:apply-templates select="@*|node()" />
        </xsl:otherwise>
      </xsl:choose>
    </xsl:copy>
  </xsl:template>

</xsl:stylesheet>