<?xml version="1.0" encoding="UTF-8"?>

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <xsl:param name="foundation-css" select="'body { width: 100% !important; min-width: 100%; margin: 0; Margin: 0; padding: 0; box-sizing: border-box; }'" />
  <xsl:param name="column-count" select="12" />
  
  <xsl:template match="@*|node()">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="html/head">
    <xsl:copy>
      <xsl:apply-templates />
      <style type="text/css">
        <xsl:value-of select="$foundation-css" />
      </style>
    </xsl:copy>    
  </xsl:template>

  <xsl:template match="row">
    <table>
      <xsl:attribute name="class">
        <xsl:choose>
          <xsl:when test="@class = ''">
            <xsl:value-of select="'row'" />
          </xsl:when>
          <xsl:otherwise>
            <xsl:value-of select="concat('row ', @class)" />
          </xsl:otherwise>
        </xsl:choose>
      </xsl:attribute>
      <xsl:apply-templates select="@*[name() != 'class']"/>
      
      <tbody>
        <tr>
          <xsl:apply-templates />
        </tr>
      </tbody>
    </table>    
  </xsl:template>

  <xsl:template match="columns">
    <xsl:variable name="sibling-count" select="count(preceding-sibling::columns) + 1 + count(following-sibling::columns)" />
    <xsl:variable name="pos" select="count(preceding-sibling::columns)" />
    <xsl:variable name="expander" select="false()" />
    
    <xsl:variable name="small-size">
      <xsl:choose>
        <xsl:when test="@small"><xsl:value-of select="@small" /></xsl:when>
        <xsl:otherwise><xsl:value-of select="$column-count" /></xsl:otherwise>
      </xsl:choose>
    </xsl:variable>    
    <xsl:variable name="large-size">
      <xsl:choose>
        <xsl:when test="@large"><xsl:value-of select="@large" /></xsl:when>
        <xsl:when test="@small"><xsl:value-of select="@small" /></xsl:when>
        <xsl:otherwise><xsl:value-of select="$column-count div $sibling-count" /></xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    
    <th>
      <xsl:attribute name="class">
        <xsl:value-of select="'columns'" />
        <xsl:if test="@class">
          <xsl:value-of select="concat(' ', @class)" />
        </xsl:if>
        <xsl:value-of select="concat(' small-', $small-size)" />
        <xsl:value-of select="concat(' large-', $large-size)" />
        <xsl:if test="$pos = 0">
          <xsl:value-of select="' first'" />
        </xsl:if>
        <xsl:if test="$pos = $sibling-count - 1">
          <xsl:value-of select="' last'" />
        </xsl:if>
      </xsl:attribute>
      <xsl:apply-templates select="@*[name() != 'class']"/>
      
      <table>
        <tbody>
          <tr>
            <th><xsl:apply-templates /></th>
<!--            
  // If the column contains a nested row, the .expander class should not be used
  // The == on the first check is because we're comparing a string pulled from $.attr() to a number
  if (largeSize == this.columnCount && col.find('.row, row').length === 0 && (noExpander == undefined || noExpander == "false") ) {
-->
            <xsl:if test="$large-size = $column-count and not(@no-expander) and not(row)">
              <th class="expander"></th>
            </xsl:if>
          </tr>
        </tbody>
      </table>
    </th>
  </xsl:template>


<!--
      var classes = ['container'];
      if (element.attr('class')) {
        classes = classes.concat(element.attr('class').split(' '));
      }

      return format('<table %s align="center" class="%s"><tbody><tr><td>%s</td></tr></tbody></table>', attrs, classes.join(' '), inner);
-->
  <xsl:template match="container">
    <table align="center">
      <xsl:attribute name="class">
        <xsl:value-of select="'container'" />
        <xsl:if test="@class">
          <xsl:value-of select="concat(' ', @class)" />
        </xsl:if>
      </xsl:attribute>
      <xsl:apply-templates select="@*[name() != 'class']"/>

      <tbody>
        <tr>
          <td><xsl:apply-templates /></td>
        </tr>
      </tbody>
    </table>
  </xsl:template>

<!--
    // <wrapper>
    case this.components.wrapper:
      var classes = ['wrapper'];
      if (element.attr('class')) {
        classes = classes.concat(element.attr('class').split(' '));
      }

      return format('<table %s class="%s" align="center"><tbody><tr><td class="wrapper-inner">%s</td></tr></tbody></table>', attrs, classes.join(' '), inner);
-->
  <xsl:template match="wrapper">
    <table align="center">
      <xsl:attribute name="class">
        <xsl:value-of select="'wrapper'" />
        <xsl:if test="@class">
          <xsl:value-of select="concat(' ', @class)" />
        </xsl:if>
      </xsl:attribute>
      <xsl:apply-templates select="@*[name() != 'class']"/>

      <tbody>
        <tr>
          <td class="wrapper-inner"><xsl:apply-templates /></td>
        </tr>
      </tbody>
    </table>
  </xsl:template>


<!--
    // <spacer>
    case this.components.spacer:
      var classes = ['spacer'];
      var size;
      var html = '';
      if (element.attr('class')) {
        classes = classes.concat(element.attr('class').split(' '));
      }
      if (element.attr('size-sm') || element.attr('size-lg')) {
        if (element.attr('size-sm')) {
          size = (element.attr('size-sm'));
          html += format('<table %s class="%s hide-for-large"><tbody><tr><td height="'+size+'px" style="font-size:'+size+'px;line-height:'+size+'px;">&nbsp;</td></tr></tbody></table>', attrs);
        }
        if (element.attr('size-lg')) {
          size = (element.attr('size-lg'));
          html += format('<table %s class="%s show-for-large"><tbody><tr><td height="'+size+'px" style="font-size:'+size+'px;line-height:'+size+'px;">&nbsp;</td></tr></tbody></table>', attrs);
        }
      } else {
        size = (element.attr('size')) || 16;
        html += format('<table %s class="%s"><tbody><tr><td height="'+size+'px" style="font-size:'+size+'px;line-height:'+size+'px;">&nbsp;</td></tr></tbody></table>', attrs);
      }

      if( element.attr('size-sm') && element.attr('size-lg') ) {
        return format(html, classes.join(' '), classes.join(' '), inner);
      }

      return format(html, classes.join(' '), inner);
-->
  <xsl:template match="spacer">
    <xsl:choose>
      <xsl:when test="@size-sm or @size-lg">
        <xsl:if test="@size-sm">
          <xsl:variable name="size" select="@size-sm" />
          <table>
            <xsl:attribute name="class">
              <xsl:value-of select="'spacer hide-for-large'" />
              <xsl:if test="@class">
                <xsl:value-of select="concat(' ', @class)" />
              </xsl:if>
            </xsl:attribute>
            <xsl:apply-templates select="@*[name() != 'class']" />
            <tbody>
              <tr>
                <td height="{$size}px" style="font-size:{$size}px;line-height:{$size}px">&#160;</td>
              </tr>
            </tbody>
          </table>
        </xsl:if>
        
        <xsl:if test="@size-lg">
          <xsl:variable name="size" select="@size-lg" />
          <table>
            <xsl:attribute name="class">
              <xsl:value-of select="'spacer show-for-large'" />
              <xsl:if test="@class">
                <xsl:value-of select="concat(' ', @class)" />
              </xsl:if>
            </xsl:attribute>
            <xsl:apply-templates select="@*[name() != 'class']" />
            <tbody>
              <tr>
                <td height="{$size}px" style="font-size:{$size}px;line-height:{$size}px">&#160;</td>
              </tr>
            </tbody>
          </table>
        </xsl:if>
      </xsl:when>
      
      <xsl:otherwise>
        <xsl:variable name="size">
          <xsl:choose>
            <xsl:when test="@size"><xsl:value-of select="@size" /></xsl:when>
            <xsl:otherwise>16</xsl:otherwise>
          </xsl:choose>
        </xsl:variable>
        <table>
          <xsl:attribute name="class">
            <xsl:value-of select="'spacer'" />
            <xsl:if test="@class">
              <xsl:value-of select="concat(' ', @class)" />
            </xsl:if>
          </xsl:attribute>
          <xsl:apply-templates select="@*[name() != 'class']" />
          <tbody>
            <tr>
              <td height="{$size}px" style="font-size:{$size}px;line-height:{$size}px">&#160;</td>
            </tr>
          </tbody>
        </table>
      </xsl:otherwise>
    </xsl:choose>

  </xsl:template>


<!--

    // <callout>
    case this.components.callout:
      var classes = ['callout-inner'];
      if (element.attr('class')) {
        classes = classes.concat(element.attr('class').split(' '));
      }

      return format('<table %s class="callout"><tbody><tr><th class="%s">%s</th><th class="expander"></th></tr></tbody></table>', attrs, classes.join(' '), inner);
-->
  <xsl:template match="callout">
    <table class="callout">
      <xsl:apply-templates select="@*[name() != 'class']"/>

      <tbody>
        <tr>
          <th>
            <xsl:attribute name="class">
              <xsl:value-of select="'callout-inner'" />
              <xsl:if test="@class">
                <xsl:value-of select="concat(' ', @class)" />
              </xsl:if>
            </xsl:attribute>
            <xsl:apply-templates />
          </th>
          <th class="expander" />
        </tr>        
      </tbody>
    </table>
  </xsl:template>

<!--
    // <menu>
    case this.components.menu:
      var classes = ['menu'];
      if (element.attr('class')) {
        classes = classes.concat(element.attr('class').split(' '));
      }
      return format('<table %s class="%s"><tbody><tr><td><table><tbody><tr>%s</tr></tbody></table></td></tr></tbody></table>', attrs, classes.join(' '), inner);
-->
  <xsl:template match="menu">
    <table>
      <xsl:attribute name="class">
        <xsl:value-of select="'menu'" />
        <xsl:if test="@class">
          <xsl:value-of select="concat(' ', @class)" />
        </xsl:if>
      </xsl:attribute>
      <xsl:apply-templates select="@*[name() != 'class']"/>

      <tbody>
        <tr>
          <td><table><tbody><tr>
            <xsl:apply-templates mode="menu-items" />
          </tr></tbody></table></td>
        </tr>
      </tbody>
    </table>
  </xsl:template>

<!--
    // <item>
    case this.components.menuItem:
      // Prepare optional target attribute for the <a> element
      var target = '';
      if (element.attr('target')) {
        target = ' target=' + element.attr('target');
      }
      var classes = ['menu-item'];
      if (element.attr('class')) {
        classes = classes.concat(element.attr('class').split(' '));
      }
      return format('<th %s class="%s"><a href="%s"%s>%s</a></th>', attrs, classes.join(' '), element.attr('href'), target, inner);
-->
  <xsl:template match="item" mode="menu-items">
    <th>
      <xsl:attribute name="class">
        <xsl:value-of select="'menu-item'" />
        <xsl:if test="@class">
          <xsl:value-of select="concat(' ', @class)" />
        </xsl:if>
        <xsl:if test="ancestor::center">
          <xsl:value-of select="concat(' float-center')" />
        </xsl:if>
      </xsl:attribute>
      <xsl:apply-templates select="@*[name() != 'class' and name() != 'target' and name() != 'href']"/>

      <a>
        <xsl:apply-templates select="@target|@href" />
        <xsl:apply-templates />
      </a>
    </th>
  </xsl:template>

  <!-- xsl:template match="*" mode="menu-items">
    <xsl:message> missing 'item' element </xsl:message>
    <th>
      <xsl:copy>
        <xsl:apply-templates />
      </xsl:copy>
    </th>
  </xsl:template>

  <xsl:template match="item">
    <xsl:message> missing 'menu' element </xsl:message>
    <a>
      <xsl:apply-templates select="@target|@href" />
      <xsl:apply-templates />
    </a>
  </xsl:template -->

<!--
    // <center>
    case this.components.center:
      if (element.children().length > 0) {
        element.children().each(function() {
          $(this).attr('align', 'center');
          $(this).addClass('float-center');
        });
        element.find('item, .menu-item').addClass('float-center');
      }

      element.attr('data-parsed', '');

      return format('%s', $.html(element, this.cheerioOpts));
-->

<!--
    // <button>
    case this.components.button:
      var expander = '';

      // Prepare optional target attribute for the <a> element
      var target = '';
      if (element.attr('target')) {
        target = ' target=' + element.attr('target');
      }

      // If we have the href attribute we can create an anchor for the inner of the button;
      if (element.attr('href')) {
        inner = format('<a %s href="%s"%s>%s</a>', attrs, element.attr('href'), target, inner);
      }

      // If the button is expanded, it needs a <center> tag around the content
      if (element.hasClass('expand') || element.hasClass('expanded')) {
        inner = format('<center>%s</center>', inner);
        expander = '\n<td class="expander"></td>';
      }

      // The .button class is always there, along with any others on the <button> element
      var classes = ['button'];
      if (element.attr('class')) {
        classes = classes.concat(element.attr('class').split(' '));
      }

      return format('<table class="%s"><tbody><tr><td><table><tbody><tr><td>%s</td></tr></tbody></table></td>%s</tr></tbody></table>', classes.join(' '), inner, expander);
-->

  <xsl:template match="button">
    <xsl:variable name="classes" select="concat(' ', @class, ' ')" />
    <table>
      <xsl:attribute name="class">
        <xsl:value-of select="'button'" />
        <xsl:if test="@class">
          <xsl:value-of select="concat(' ', @class)" />
        </xsl:if>
      </xsl:attribute>
      <xsl:apply-templates select="@*[name() != 'class' and name() != 'target' and name() != 'href']"/>
      <tbody>
        <tr>
          <td><table>
            <tbody>
              <tr>
                <td>
                  <xsl:choose>
                    <xsl:when test="contains($classes, ' expand ') or contains($classes, ' expanded ')">
                      <center><xsl:call-template name="link" /></center>
                    </xsl:when>
                    <xsl:otherwise>
                      <xsl:call-template name="link" />
                    </xsl:otherwise>
                  </xsl:choose>
                </td>
              </tr>
            </tbody>
          </table></td>
          <xsl:if test="contains($classes, ' expand ') or contains($classes, ' expanded ')">
            <td class="expander" />
          </xsl:if>
        </tr>
      </tbody>
    </table>
  </xsl:template>

  <xsl:template name="link">
    <a>
      <xsl:apply-templates select="@target|@href" />
      <xsl:apply-templates />
    </a>    
  </xsl:template>

</xsl:stylesheet>