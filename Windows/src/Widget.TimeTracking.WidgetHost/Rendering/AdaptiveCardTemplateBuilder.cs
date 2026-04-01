using System.Globalization;
using System.Text;
using System.Text.Json;
using Widget.TimeTracking.Core.Enums;

namespace Widget.TimeTracking.WidgetHost.Rendering;

internal static class AdaptiveCardTemplateBuilder
{
    private const int MaxInlineProfilePhotoUrlLength = 48 * 1024;
    private static readonly string DefaultWidgetCardSurfaceBg = BuildWidgetCardSurfaceBg();

    #region Composite button SVGs (background rect + white icon baked into one SVG)

    // Each button is a 44x44 SVG with a rounded-rect fill and the icon paths centered inside.
    // Café: margen 2 a la izquierda del icono → translate(10,7) en lugar de (8,7) como comida.
    // This avoids backgroundImage issues in the widget host and guarantees no deformation.

    private static readonly string EntryBlue = SvgDataUri(
        "<svg xmlns='http://www.w3.org/2000/svg' width='44' height='44' viewBox='0 0 44 44'>"
        + "<rect width='44' height='44' rx='12' fill='#FFFFFF' stroke='#5F96F9' stroke-width='1'/>"
        + "<g transform='translate(10,10) scale(1.6)'>"
        + "<path d='M11.0255 4.9058C13.6582 6.33196 13.6582 8.66804 11.0255 10.0942L8.90142 11.2429L6.77738 12.3916C4.15154 13.8177 2 12.6497 2 9.79736V7.5V5.20264C2 2.35031 4.15154 1.18227 6.78425 2.60844L8.33089 3.44736' stroke='#5F96F9' stroke-width='1.5' stroke-miterlimit='10' stroke-linecap='round' stroke-linejoin='round' fill='none'/>"
        + "</g></svg>");

    private static readonly string EntryDisabled = SvgDataUri(
        "<svg xmlns='http://www.w3.org/2000/svg' width='44' height='44' viewBox='0 0 44 44'>"
        + "<rect width='44' height='44' rx='12' fill='#5F96F9'/>"
        + "<g transform='translate(10,10) scale(1.6)'>"
        + "<path d='M11.0255 4.9058C13.6582 6.33196 13.6582 8.66804 11.0255 10.0942L8.90142 11.2429L6.77738 12.3916C4.15154 13.8177 2 12.6497 2 9.79736V7.5V5.20264C2 2.35031 4.15154 1.18227 6.78425 2.60844L8.33089 3.44736' stroke='white' stroke-width='1.5' stroke-miterlimit='10' stroke-linecap='round' stroke-linejoin='round' fill='none'/>"
        + "</g></svg>");

    private static readonly string StopBlue = SvgDataUri(
        "<svg xmlns='http://www.w3.org/2000/svg' width='44' height='44' viewBox='0 0 44 44'>"
        + "<rect width='44' height='44' rx='12' fill='#5F96F9'/>"
        + "<g transform='translate(8,8)'>"
        + "<rect x='4' y='4' width='20' height='20' rx='5' stroke='white' stroke-width='2.5' fill='none'/>"
        + "</g></svg>");

    private static readonly string StopDisabled = SvgDataUri(
        "<svg xmlns='http://www.w3.org/2000/svg' width='44' height='44' viewBox='0 0 44 44'>"
        + "<rect width='44' height='44' rx='12' fill='#5F96F9'/>"
        + "<g transform='translate(8,8)'>"
        + "<rect x='4' y='4' width='20' height='20' rx='5' stroke='white' stroke-width='2.5' fill='none'/>"
        + "</g></svg>");

    private static readonly string CoffeeBlue = SvgDataUri(
        "<svg xmlns='http://www.w3.org/2000/svg' width='44' height='44' viewBox='0 0 44 44'>"
        + "<rect width='44' height='44' rx='12' fill='#5F96F9'/>"
        + "<g transform='translate(10,7) scale(1.85)' fill='none' stroke='white' stroke-width='1.2' stroke-linecap='round' stroke-linejoin='round'>"
        + "<path d='M1.25 8.468V6.548C1.25 5.088 2.43 3.918 3.88 3.918H8.49C9.95 3.918 11.12 5.098 11.12 6.548V11.128C11.12 12.588 9.94 13.758 8.49 13.758H3.88C2.43 13.758 1.25 12.578 1.25 11.128'/>"
        + "<path d='M3.44 1.968V1.408'/>"
        + "<path d='M5.94 1.968V1.408'/>"
        + "<path d='M8.44 1.968V1.408'/>"
        + "<path d='M13.75 8.228C13.75 9.678 12.57 10.858 11.12 10.858V5.598C12.57 5.598 13.75 6.778 13.75 8.228Z'/>"
        + "<path d='M1.25 7.498H10.94'/>"
        + "</g></svg>");

    private static readonly string CoffeeBreak = SvgDataUri(
        "<svg xmlns='http://www.w3.org/2000/svg' width='44' height='44' viewBox='0 0 44 44'>"
        + "<rect width='44' height='44' rx='12' fill='#FFFFFF' stroke='#5F96F9' stroke-width='1'/>"
        + "<g transform='translate(10,7) scale(1.85)' fill='none' stroke='#5F96F9' stroke-width='1.2' stroke-linecap='round' stroke-linejoin='round'>"
        + "<path d='M1.25 8.468V6.548C1.25 5.088 2.43 3.918 3.88 3.918H8.49C9.95 3.918 11.12 5.098 11.12 6.548V11.128C11.12 12.588 9.94 13.758 8.49 13.758H3.88C2.43 13.758 1.25 12.578 1.25 11.128'/>"
        + "<path d='M3.44 1.968V1.408'/>"
        + "<path d='M5.94 1.968V1.408'/>"
        + "<path d='M8.44 1.968V1.408'/>"
        + "<path d='M13.75 8.228C13.75 9.678 12.57 10.858 11.12 10.858V5.598C12.57 5.598 13.75 6.778 13.75 8.228Z'/>"
        + "<path d='M1.25 7.498H10.94'/>"
        + "</g></svg>");

    private static readonly string CoffeeDisabled = SvgDataUri(
        "<svg xmlns='http://www.w3.org/2000/svg' width='44' height='44' viewBox='0 0 44 44'>"
        + "<rect width='44' height='44' rx='12' fill='#5F96F9'/>"
        + "<g transform='translate(10,7) scale(1.85)' fill='none' stroke='white' stroke-width='1.2' stroke-linecap='round' stroke-linejoin='round'>"
        + "<path d='M1.25 8.468V6.548C1.25 5.088 2.43 3.918 3.88 3.918H8.49C9.95 3.918 11.12 5.098 11.12 6.548V11.128C11.12 12.588 9.94 13.758 8.49 13.758H3.88C2.43 13.758 1.25 12.578 1.25 11.128'/>"
        + "<path d='M3.44 1.968V1.408'/>"
        + "<path d='M5.94 1.968V1.408'/>"
        + "<path d='M8.44 1.968V1.408'/>"
        + "<path d='M13.75 8.228C13.75 9.678 12.57 10.858 11.12 10.858V5.598C12.57 5.598 13.75 6.778 13.75 8.228Z'/>"
        + "<path d='M1.25 7.498H10.94'/>"
        + "</g></svg>");

    private static readonly string FoodBlue = SvgDataUri(
        "<svg xmlns='http://www.w3.org/2000/svg' width='44' height='44' viewBox='0 0 44 44'>"
        + "<rect width='44' height='44' rx='12' fill='#5F96F9'/>"
        + "<g transform='translate(8,7) scale(1.85)' fill='none' stroke='white' stroke-width='1.2' stroke-linecap='round' stroke-linejoin='round'>"
        + "<path d='M10.9615 14C10.3959 13.998 9.9389 13.538 9.941 12.972L9.956 8.875L12.005 8.882L11.99 12.98C11.988 13.545 11.527 14.002 10.9615 14Z'/>"
        + "<path d='M12.034 1.029L12.005 8.882L8.932 8.871L8.95 4.091C8.956 2.394 10.337 1.023 12.034 1.029Z'/>"
        + "<path d='M7.071 4.122L7.109 1.05'/>"
        + "<path d='M4.903 13.999C4.337 13.992 3.884 13.528 3.891 12.962L3.941 8.865L5.989 8.89L5.939 12.987C5.932 13.553 5.468 14.006 4.903 13.999Z'/>"
        + "<path d='M5.681 6.154L4.315 6.138C3.561 6.129 2.957 5.51 2.966 4.756L2.974 4.073L7.071 4.122L7.063 4.805C7.054 5.559 6.435 6.163 5.681 6.154Z'/>"
        + "<path d='M2.974 4.073L3.011 1'/>"
        + "<path d='M5.023 4.098L5.06 1.025'/>"
        + "<path d='M4.965 8.877L4.998 6.146'/>"
        + "</g></svg>");

    private static readonly string FoodBreak = SvgDataUri(
        "<svg xmlns='http://www.w3.org/2000/svg' width='44' height='44' viewBox='0 0 44 44'>"
        + "<rect width='44' height='44' rx='12' fill='#FFFFFF' stroke='#5F96F9' stroke-width='1'/>"
        + "<g transform='translate(8,7) scale(1.85)' fill='none' stroke='#5F96F9' stroke-width='1.2' stroke-linecap='round' stroke-linejoin='round'>"
        + "<path d='M10.9615 14C10.3959 13.998 9.9389 13.538 9.941 12.972L9.956 8.875L12.005 8.882L11.99 12.98C11.988 13.545 11.527 14.002 10.9615 14Z'/>"
        + "<path d='M12.034 1.029L12.005 8.882L8.932 8.871L8.95 4.091C8.956 2.394 10.337 1.023 12.034 1.029Z'/>"
        + "<path d='M7.071 4.122L7.109 1.05'/>"
        + "<path d='M4.903 13.999C4.337 13.992 3.884 13.528 3.891 12.962L3.941 8.865L5.989 8.89L5.939 12.987C5.932 13.553 5.468 14.006 4.903 13.999Z'/>"
        + "<path d='M5.681 6.154L4.315 6.138C3.561 6.129 2.957 5.51 2.966 4.756L2.974 4.073L7.071 4.122L7.063 4.805C7.054 5.559 6.435 6.163 5.681 6.154Z'/>"
        + "<path d='M2.974 4.073L3.011 1'/>"
        + "<path d='M5.023 4.098L5.06 1.025'/>"
        + "<path d='M4.965 8.877L4.998 6.146'/>"
        + "</g></svg>");

    private static readonly string FoodDisabled = SvgDataUri(
        "<svg xmlns='http://www.w3.org/2000/svg' width='44' height='44' viewBox='0 0 44 44'>"
        + "<rect width='44' height='44' rx='12' fill='#5F96F9'/>"
        + "<g transform='translate(8,7) scale(1.85)' fill='none' stroke='white' stroke-width='1.2' stroke-linecap='round' stroke-linejoin='round'>"
        + "<path d='M10.9615 14C10.3959 13.998 9.9389 13.538 9.941 12.972L9.956 8.875L12.005 8.882L11.99 12.98C11.988 13.545 11.527 14.002 10.9615 14Z'/>"
        + "<path d='M12.034 1.029L12.005 8.882L8.932 8.871L8.95 4.091C8.956 2.394 10.337 1.023 12.034 1.029Z'/>"
        + "<path d='M7.071 4.122L7.109 1.05'/>"
        + "<path d='M4.903 13.999C4.337 13.992 3.884 13.528 3.891 12.962L3.941 8.865L5.989 8.89L5.939 12.987C5.932 13.553 5.468 14.006 4.903 13.999Z'/>"
        + "<path d='M5.681 6.154L4.315 6.138C3.561 6.129 2.957 5.51 2.966 4.756L2.974 4.073L7.071 4.122L7.063 4.805C7.054 5.559 6.435 6.163 5.681 6.154Z'/>"
        + "<path d='M2.974 4.073L3.011 1'/>"
        + "<path d='M5.023 4.098L5.06 1.025'/>"
        + "<path d='M4.965 8.877L4.998 6.146'/>"
        + "</g></svg>");

    private static readonly string LastShiftIcon = SvgDataUri(
        "<svg xmlns='http://www.w3.org/2000/svg' width='20' height='20' viewBox='0 0 20 20'>"
        + "<g fill='none' stroke='#B5CCF5' stroke-width='1.5' stroke-linecap='round' stroke-linejoin='round'>"
        + "<circle cx='10' cy='10' r='6.25'/>"
        + "<path d='M10 7.25V10.25L12.25 11.5'/>"
        + "</g></svg>");

    private static readonly string CoffeeSummaryIcon = SvgDataUri(
        "<svg xmlns='http://www.w3.org/2000/svg' width='20' height='20' viewBox='0 0 20 20'>"
        + "<g transform='translate(3,3)' fill='none' stroke='#B5CCF5' stroke-width='1.3' stroke-linecap='round' stroke-linejoin='round'>"
        + "<path d='M1.25 8.468V6.548C1.25 5.088 2.43 3.918 3.88 3.918H8.49C9.95 3.918 11.12 5.098 11.12 6.548V11.128C11.12 12.588 9.94 13.758 8.49 13.758H3.88C2.43 13.758 1.25 12.578 1.25 11.128'/>"
        + "<path d='M3.44 1.968V1.408'/>"
        + "<path d='M5.94 1.968V1.408'/>"
        + "<path d='M8.44 1.968V1.408'/>"
        + "<path d='M13.75 8.228C13.75 9.678 12.57 10.858 11.12 10.858V5.598C12.57 5.598 13.75 6.778 13.75 8.228Z'/>"
        + "<path d='M1.25 7.498H10.94'/>"
        + "</g></svg>");

    private static readonly string FoodSummaryIcon = SvgDataUri(
        "<svg xmlns='http://www.w3.org/2000/svg' width='20' height='20' viewBox='0 0 20 20'>"
        + "<g transform='translate(3,3)' fill='none' stroke='#B5CCF5' stroke-width='1.3' stroke-linecap='round' stroke-linejoin='round'>"
        + "<path d='M10.9615 14C10.3959 13.998 9.9389 13.538 9.941 12.972L9.956 8.875L12.005 8.882L11.99 12.98C11.988 13.545 11.527 14.002 10.9615 14Z'/>"
        + "<path d='M12.034 1.029L12.005 8.882L8.932 8.871L8.95 4.091C8.956 2.394 10.337 1.023 12.034 1.029Z'/>"
        + "<path d='M7.071 4.122L7.109 1.05'/>"
        + "<path d='M4.903 13.999C4.337 13.992 3.884 13.528 3.891 12.962L3.941 8.865L5.989 8.89L5.939 12.987C5.932 13.553 5.468 14.006 4.903 13.999Z'/>"
        + "<path d='M5.681 6.154L4.315 6.138C3.561 6.129 2.957 5.51 2.966 4.756L2.974 4.073L7.071 4.122L7.063 4.805C7.054 5.559 6.435 6.163 5.681 6.154Z'/>"
        + "<path d='M2.974 4.073L3.011 1'/>"
        + "<path d='M5.023 4.098L5.06 1.025'/>"
        + "<path d='M4.965 8.877L4.998 6.146'/>"
        + "</g></svg>");

    /// <summary>CTA sesión cerrada: mismo tamaño que el resto de botones (44×44, #5F96F9 + icono logout).</summary>
    private static readonly string OpenAppBlue = SvgDataUri(
        "<svg xmlns='http://www.w3.org/2000/svg' width='44' height='44' viewBox='0 0 44 44'>"
        + "<rect width='44' height='44' rx='12' fill='#5F96F9'/>"
        + "<g transform='translate(10,10) scale(0.889)' fill='none' stroke='white' stroke-width='1.97402' stroke-miterlimit='10' stroke-linecap='round' stroke-linejoin='round'>"
        + "<path d='M19.1222 10.3525L21.9297 13.16L19.1222 15.9675'/>"
        + "<path d='M16.1895 13.1602H21.8594'/>"
        + "<path d='M10.7021 13.1602H12.8516'/>"
        + "<path d='M6.56521 19.2139C5.20532 17.7224 4.38281 15.6716 4.38281 13.1602C4.38281 7.6768 8.30893 4.38676 13.1562 4.38676'/>"
        + "<path d='M13.1562 21.9336C12.0047 21.9336 10.9081 21.7472 9.89911 21.3962'/>"
        + "</g></svg>");

    #endregion

    private static readonly JsonSerializerOptions SerializerOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    private static string? _compiledTemplate;

    // Every button is a single Image element (composite SVG with baked-in background).
    // selectAction on the parent Container handles the tap.
    // No ActionSet, no style:"positive", no backgroundImage — zero deformation.
    // TextBlock/Container enums in JSON must be lowercase (e.g. dark, bolder, cover): the Widgets host is strict and PascalCase can drop foreground so text vanishes.
    private static readonly string RawTemplate = """
    {
      "$schema": "http://adaptivecards.io/schemas/adaptive-card.json",
      "type": "AdaptiveCard",
      "version": "1.5",
      "padding": "none",
      "backgroundImage": {
        "url": "${widgetCardSurfaceBg}",
        "fillMode": "cover"
      },
      "body": [
        {
          "type": "Container",
          "bleed": true,
          "style": "default",
          "items": [
            {
              "type": "ColumnSet",
              "$when": "${isSignedIn}",
              "spacing": "none",
              "verticalContentAlignment": "center",
              "columns": [
                {
                  "type": "Column",
                  "width": "stretch",
                  "items": [
                    {
                      "type": "Container",
                      "spacing": "none",
                      "minHeight": "17px"
                    },
                    {
                      "type": "TextBlock",
                      "text": "${displayName}",
                      "spacing": "small",
                      "weight": "bolder",
                      "color": "dark",
                      "wrap": true
                    }
                  ]
                },
                {
                  "type": "Column",
                  "width": "auto",
                  "verticalContentAlignment": "center",
                  "items": [
                    {
                      "type": "Container",
                      "spacing": "none",
                      "minHeight": "15px"
                    },
                    {
                      "type": "Container",
                      "$when": "${hasProfilePhoto}",
                      "spacing": "none",
                      "items": [
                        {
                          "type": "Image",
                          "url": "${profilePhotoUrl}",
                          "width": "32px",
                          "height": "32px",
                          "style": "person",
                          "backgroundColor": "#FFFFFF",
                          "horizontalAlignment": "right",
                          "altText": "Foto de perfil"
                        }
                      ]
                    }
                  ]
                }
              ]
            },
            {
              "type": "TextBlock",
              "$when": "${isSignedOut}",
              "text": "${message}",
              "color": "dark",
              "wrap": true,
              "spacing": "small"
            },
            {
              "type": "Container",
              "$when": "${isSignedOut}",
              "spacing": "medium",
              "selectAction": { "type": "Action.Execute", "title": "Log In", "verb": "open-app" },
              "items": [
                {
                  "type": "Image",
                  "url": "{{OpenAppBlue}}",
                  "width": "44px",
                  "horizontalAlignment": "center"
                }
              ]
            },
            {
              "type": "ColumnSet",
              "$when": "${isSignedIn}",
              "spacing": "medium",
              "verticalContentAlignment": "center",
              "columns": [
                {
                  "type": "Column",
                  "width": "stretch",
                  "verticalContentAlignment": "center",
                  "items": [
                    {
                      "type": "Container",
                      "style": "default",
                      "items": [
                        {
                          "type": "Image",
                          "url": "${timerChipSvg}",
                          "width": "110px",
                          "height": "44px",
                          "horizontalAlignment": "left",
                          "spacing": "none"
                        }
                      ]
                    }
                  ]
                },
                {
                  "type": "Column",
                  "width": "auto",
                  "items": [
                    {
                      "type": "ColumnSet",
                      "spacing": "none",
                      "verticalContentAlignment": "center",
                      "columns": [
                        {
                          "type": "Column",
                          "width": "auto",
                          "items": [
                            {
                              "type": "Container",
                              "$when": "${showPrimaryActionInteractive}",
                              "selectAction": { "type": "Action.Execute", "title": "${primaryActionTitle}", "verb": "${primaryActionVerb}" },
                              "items": [
                                { "type": "Image", "url": "${primaryActionButton}", "width": "44px" }
                              ]
                            },
                            {
                              "type": "Container",
                              "$when": "${showPrimaryActionDisabled}",
                              "items": [
                                { "type": "Image", "url": "{{StopDisabled}}", "width": "44px" }
                              ]
                            }
                          ]
                        },
                        {
                          "type": "Column",
                          "width": "auto",
                          "items": [
                            {
                              "type": "Container",
                              "$when": "${showCoffeeActive}",
                              "selectAction": { "type": "Action.Execute", "title": "Descanso", "verb": "${coffeeVerb}" },
                              "items": [
                                { "type": "Image", "url": "{{CoffeeBlue}}", "width": "44px" }
                              ]
                            },
                            {
                              "type": "Container",
                              "$when": "${showCoffeeEndBreak}",
                              "selectAction": { "type": "Action.Execute", "title": "Fin descanso", "verb": "${coffeeVerb}" },
                              "items": [
                                { "type": "Image", "url": "{{CoffeeBreak}}", "width": "44px" }
                              ]
                            },
                            {
                              "type": "Container",
                              "$when": "${showCoffeeDisabled}",
                              "items": [
                                { "type": "Image", "url": "{{CoffeeDisabled}}", "width": "44px" }
                              ]
                            }
                          ]
                        },
                        {
                          "type": "Column",
                          "width": "auto",
                          "items": [
                            {
                              "type": "Container",
                              "$when": "${showFoodActive}",
                              "selectAction": { "type": "Action.Execute", "title": "Comida", "verb": "${foodVerb}" },
                              "items": [
                                { "type": "Image", "url": "{{FoodBlue}}", "width": "44px" }
                              ]
                            },
                            {
                              "type": "Container",
                              "$when": "${showFoodEndBreak}",
                              "selectAction": { "type": "Action.Execute", "title": "Fin comida", "verb": "${foodVerb}" },
                              "items": [
                                { "type": "Image", "url": "{{FoodBreak}}", "width": "44px" }
                              ]
                            },
                            {
                              "type": "Container",
                              "$when": "${showFoodDisabled}",
                              "items": [
                                { "type": "Image", "url": "{{FoodDisabled}}", "width": "44px" }
                              ]
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
              ]
            },
            {
              "type": "Container",
              "$when": "${isSignedIn}",
              "style": "default",
              "spacing": "medium",
              "padding": "default",
              "separator": true,
              "items": [
                {
                  "type": "TextBlock",
                  "text": "${statusHeadline}",
                  "size": "large",
                  "weight": "bolder",
                  "color": "dark",
                  "wrap": true,
                  "spacing": "small"
                },
                {
                  "type": "TextBlock",
                  "text": "${statusDetail}",
                  "size": "small",
                  "color": "dark",
                  "weight": "lighter",
                  "wrap": true,
                  "spacing": "small"
                },
                {
                  "type": "ColumnSet",
                  "spacing": "medium",
                  "horizontalAlignment": "center",
                  "columns": [
                    {
                      "type": "Column",
                      "width": "auto",
                      "items": [
                        {
                          "type": "ColumnSet",
                          "spacing": "none",
                          "columns": [
                            {
                              "type": "Column",
                              "width": "auto",
                              "items": [
                                {
                                  "type": "Image",
                                  "url": "{{LastShiftIcon}}",
                                  "width": "16px",
                                  "height": "16px"
                                }
                              ]
                            },
                            {
                              "type": "Column",
                              "width": "stretch",
                              "verticalContentAlignment": "center",
                              "items": [
                                {
                                  "type": "TextBlock",
                                  "text": "Jornada",
                                  "size": "small",
                                  "color": "dark",
                                  "weight": "lighter",
                                  "spacing": "none",
                                  "wrap": true
                                }
                              ]
                            }
                          ]
                        },
                        {
                          "type": "TextBlock",
                          "text": "${lastCompletedShiftDuration}",
                          "size": "medium",
                          "color": "dark",
                          "weight": "bolder",
                          "spacing": "small",
                          "wrap": true
                        }
                      ]
                    },
                    {
                      "type": "Column",
                      "width": "auto",
                      "items": [
                        {
                          "type": "ColumnSet",
                          "spacing": "none",
                          "columns": [
                            {
                              "type": "Column",
                              "width": "auto",
                              "items": [
                                {
                                  "type": "Image",
                                  "url": "{{CoffeeSummaryIcon}}",
                                  "width": "16px",
                                  "height": "16px"
                                }
                              ]
                            },
                            {
                              "type": "Column",
                              "width": "stretch",
                              "verticalContentAlignment": "center",
                              "items": [
                                {
                                  "type": "TextBlock",
                                  "text": "Descanso",
                                  "size": "small",
                                  "color": "dark",
                                  "weight": "lighter",
                                  "spacing": "none",
                                  "wrap": true
                                }
                              ]
                            }
                          ]
                        },
                        {
                          "type": "TextBlock",
                          "text": "${coffeeTodayDuration}",
                          "size": "medium",
                          "color": "dark",
                          "weight": "bolder",
                          "spacing": "small",
                          "wrap": true
                        }
                      ]
                    },
                    {
                      "type": "Column",
                      "width": "auto",
                      "items": [
                        {
                          "type": "ColumnSet",
                          "spacing": "none",
                          "columns": [
                            {
                              "type": "Column",
                              "width": "auto",
                              "items": [
                                {
                                  "type": "Image",
                                  "url": "{{FoodSummaryIcon}}",
                                  "width": "16px",
                                  "height": "16px"
                                }
                              ]
                            },
                            {
                              "type": "Column",
                              "width": "stretch",
                              "verticalContentAlignment": "center",
                              "items": [
                                {
                                  "type": "TextBlock",
                                  "text": "Comida",
                                  "size": "small",
                                  "color": "dark",
                                  "weight": "lighter",
                                  "spacing": "none",
                                  "wrap": true
                                }
                              ]
                            }
                          ]
                        },
                        {
                          "type": "TextBlock",
                          "text": "${foodTodayDuration}",
                          "size": "medium",
                          "color": "dark",
                          "weight": "bolder",
                          "spacing": "small",
                          "wrap": true
                        }
                      ]
                    }
                  ]
                }
              ]
            },
            {
              "type": "Container",
              "$when": "${isSignedIn && $host.widgetSize == \"large\"}",
              "spacing": "large",
              "items": [
                {
                  "type": "TextBlock",
                  "text": "Última acción: ${lastAction}",
                  "size": "small",
                  "color": "dark",
                  "weight": "lighter",
                  "wrap": true
                },
                {
                  "type": "TextBlock",
                  "text": "Hora: ${lastActionTime}",
                  "size": "small",
                  "color": "dark",
                  "weight": "lighter",
                  "spacing": "small",
                  "wrap": true
                }
              ]
            },
            {
              "type": "TextBlock",
              "$when": "${isSignedIn && $host.widgetSize == \"large\"}",
              "text": "${timelineText}",
              "color": "dark",
              "weight": "lighter",
              "wrap": true,
              "spacing": "medium",
              "maxLines": 2,
              "size": "small"
            }
          ]
        }
      ]
    }
    """;

    public static string BuildTemplate() =>
        _compiledTemplate ??= CompileTemplate();

    private static string CompileTemplate() =>
        RawTemplate
            .Replace("{{EntryBlue}}", EntryBlue)
            .Replace("{{EntryDisabled}}", EntryDisabled)
            .Replace("{{StopBlue}}", StopBlue)
            .Replace("{{StopDisabled}}", StopDisabled)
            .Replace("{{LastShiftIcon}}", LastShiftIcon)
            .Replace("{{CoffeeSummaryIcon}}", CoffeeSummaryIcon)
            .Replace("{{FoodSummaryIcon}}", FoodSummaryIcon)
            .Replace("{{CoffeeBlue}}", CoffeeBlue)
            .Replace("{{CoffeeBreak}}", CoffeeBreak)
            .Replace("{{CoffeeDisabled}}", CoffeeDisabled)
            .Replace("{{FoodBlue}}", FoodBlue)
            .Replace("{{FoodBreak}}", FoodBreak)
            .Replace("{{FoodDisabled}}", FoodDisabled)
            .Replace("{{OpenAppBlue}}", OpenAppBlue);

    private static string SvgDataUri(string svg) =>
        "data:image/svg+xml," + Uri.EscapeDataString(svg);

    private static string BuildWidgetCardSurfaceBg()
    {
        var svg = new StringBuilder(256);
        svg.Append("<svg xmlns='http://www.w3.org/2000/svg' width='400' height='800' viewBox='0 0 400 800'>");
        // Fondo general ligeramente azulado.
        svg.Append("<rect width='400' height='800' fill='#E5F1FF'/>");
        // Franja superior azul (barra de título).
        svg.Append("<rect x='4' y='4' width='392' height='55' rx='10' fill='#5F96F9'/>");
        // Sombra suave detrás de la tarjeta principal.
        svg.Append("<rect x='16' y='68' width='368' height='350' rx='18' fill='#B5CCF5' opacity='0.35'/>");
        // Tarjeta principal blanca con borde sutil.
        svg.Append("<rect x='10' y='202' width='380' height='450' rx='18' fill='#FFFFFF' stroke='#D5E5FA' stroke-width='1'/>");
        svg.Append("</svg>");
        return SvgDataUri(svg.ToString());
    }

    private static string SanitizeProfilePhotoUrl(string? value)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            return string.Empty;
        }

        if ((value.StartsWith("data:image/png;base64,", StringComparison.OrdinalIgnoreCase)
                || value.StartsWith("data:image/jpeg;base64,", StringComparison.OrdinalIgnoreCase))
            && value.Length <= MaxInlineProfilePhotoUrlLength)
        {
            return value;
        }

        return Uri.TryCreate(value, UriKind.Absolute, out var uri) && uri.Scheme == Uri.UriSchemeHttps
            ? value
            : string.Empty;
    }

    private static string BuildTimerChipSvg(string sessionCounter, bool isActive)
    {
        var normalizedCounter = NormalizeTimerCounter(sessionCounter);
        var bulletColor = isActive ? "#10B981" : "#EF4444";
        var glyphMarkup = BuildTimerGlyphMarkup(normalizedCounter, bulletColor);
        return SvgDataUri(
            "<svg xmlns='http://www.w3.org/2000/svg' width='110' height='44' viewBox='0 0 110 44'>"
            + "<rect x='0.5' y='0.5' width='109' height='43' rx='12' ry='12' fill='#FFFFFF' stroke='#D2ECFF' stroke-width='1'/>"
            + glyphMarkup
            + "</svg>");
    }

    private static string NormalizeTimerCounter(string? value)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            return "00:00:00";
        }

        var candidate = new StringBuilder(8);
        foreach (var character in value)
        {
            if (char.IsDigit(character) || character == ':')
            {
                candidate.Append(character);
            }
        }

        var normalized = candidate.ToString();
        return normalized.Length == 8 && normalized[2] == ':' && normalized[5] == ':'
            ? normalized
            : "00:00:00";
    }

    private static string BuildTimerGlyphMarkup(string timerText, string bulletColor)
    {
        const double bulletDiameter = 4;
        const double bulletGap = 9.1;
        const double chipWidth = 110;
        const double digitAdvance = 8.7;
        const double colonAdvance = 4.8;

        var markup = new StringBuilder(512);
        var timerWidth = MeasureTimerGlyphWidth(timerText, digitAdvance, colonAdvance);
        var groupStartX = (chipWidth - (bulletDiameter + bulletGap + timerWidth)) / 2;
        var cursor = groupStartX + bulletDiameter + bulletGap;

        markup.Append($"<circle cx='{SvgValue(groupStartX + (bulletDiameter / 2))}' cy='22' r='1.9' fill='{bulletColor}' opacity='1'/>");
        markup.Append("<g fill='none' stroke='#111827' stroke-width='1.62' stroke-linecap='round' stroke-linejoin='round'>");

        foreach (var character in timerText)
        {
            if (character == ':')
            {
                AppendColonMarkup(markup, cursor);
                cursor += colonAdvance;
                continue;
            }

            if (char.IsDigit(character))
            {
                AppendDigitMarkup(markup, character - '0', cursor);
                cursor += digitAdvance;
            }
        }

        markup.Append("</g>");
        return markup.ToString();
    }

    private static double MeasureTimerGlyphWidth(string timerText, double digitAdvance, double colonAdvance)
    {
        var width = 0d;
        for (var index = 0; index < timerText.Length; index++)
        {
            width += timerText[index] == ':' ? colonAdvance : digitAdvance;
        }

        return width;
    }

    private static void AppendDigitMarkup(StringBuilder markup, int digit, double x)
    {
        var digitPath = GetMonospaceDigitPath(digit);
        if (!string.IsNullOrEmpty(digitPath))
        {
            markup.Append($"<path d='{digitPath}' transform='translate({SvgValue(x)},16.85) scale(1.03)'/>");
        }
    }

    private static string GetMonospaceDigitPath(int digit) =>
        digit switch
        {
            0 => "M1.4 0.8H4.6L5.8 2V8L4.6 9.2H1.4L0.2 8V2L1.4 0.8Z",
            1 => "M1.7 2.3L3.2 0.8V9.2M1.6 9.2H4.8M1.7 2.3H3.1",
            2 => "M0.7 2.1L2 0.8H4.8L5.9 1.9V3.3L0.8 9.2H5.9",
            3 => "M0.8 1.2H4.7L5.9 2.3V4L4.8 5H2.2M4.8 5L5.9 6V7.7L4.7 8.8H0.8",
            4 => "M4.9 0.8V9.2M0.8 5H5.8M0.8 5L3.7 0.8",
            5 => "M5.8 0.8H1.3V4.8H4.7L5.9 6V7.9L4.8 9.2H0.9",
            6 => "M5.1 0.8H1.9L0.8 1.9V8.1L1.9 9.2H4.8L5.9 8.1V6L4.8 4.8H0.9",
            7 => "M0.8 0.8H5.9L2.8 9.2",
            8 => "M1.8 0.8H4.7L5.8 1.9V3.8L4.7 5H1.8L0.8 3.8V1.9L1.8 0.8ZM1.8 5H4.7L5.8 6.1V8L4.7 9.2H1.8L0.8 8V6.1L1.8 5Z",
            9 => "M5.8 5H1.8L0.8 3.9V1.9L1.8 0.8H4.8L5.8 1.9V8.1L4.8 9.2H1.1",
            _ => string.Empty
        };

    private static void AppendColonMarkup(StringBuilder markup, double x)
    {
        var centerX = SvgValue(x + 1.1);
        markup.Append($"<circle cx='{centerX}' cy='19.35' r='1.05' fill='#111827' stroke='none' opacity='1'/><circle cx='{centerX}' cy='24.65' r='1.05' fill='#111827' stroke='none' opacity='1'/>");
    }

    private static string SvgValue(double value) =>
        value.ToString("0.###", CultureInfo.InvariantCulture);

    public static string BuildData(TimeTrackingWidgetViewModel viewModel) =>
        viewModel switch
        {
            SignedOutWidgetViewModel signedOut => JsonSerializer.Serialize(
                new
                {
                    signedOut.Title,
                    signedOut.CustomState,
                    signedOut.SurfaceColorHex,
                    signedOut.AccentColorHex,
                    IsSignedOut = true,
                    IsSignedIn = false,
                    signedOut.Message,
                    signedOut.PrimaryActionLabel,
                    WidgetCardSurfaceBg = DefaultWidgetCardSurfaceBg,
                    DisplayName = string.Empty,
                    StatusHeadline = string.Empty,
                    StatusDetail = string.Empty,
                    SessionCounter = "00:00:00",
                    LastAction = string.Empty,
                    LastActionTime = string.Empty,
                    LastCompletedShiftDuration = string.Empty,
                    WorkedThisMonthDuration = string.Empty,
                    CoffeeTodayDuration = string.Empty,
                    FoodTodayDuration = string.Empty,
                    TimelineText = string.Empty,
                    TimerChipSvg = BuildTimerChipSvg("00:00:00", false),
                    CoffeeVerb = "start-coffee-break",
                    FoodVerb = "start-food-break",
                    ShowPrimaryActionInteractive = false,
                    ShowPrimaryActionDisabled = false,
                    PrimaryActionButton = string.Empty,
                    PrimaryActionTitle = string.Empty,
                    PrimaryActionVerb = string.Empty,
                    ShowCoffeeActive = false,
                    ShowCoffeeEndBreak = false,
                    ShowCoffeeDisabled = false,
                    ShowFoodActive = false,
                    ShowFoodEndBreak = false,
                    ShowFoodDisabled = false,
                    HasProfilePhoto = false,
                    ProfilePhotoUrl = string.Empty
                },
                SerializerOptions),
            SignedInWidgetViewModel signedIn => BuildSignedInData(signedIn),
            _ => throw new InvalidOperationException(
                $"Unsupported widget view model type: {viewModel.GetType().Name}")
        };

    private static string BuildSignedInData(SignedInWidgetViewModel signedIn)
    {
        var safeProfilePhotoUrl = SanitizeProfilePhotoUrl(signedIn.ProfilePhotoUrl);

        var isActiveSession =
            signedIn.ActiveBreakType == BreakType.None
            && !signedIn.CanClockIn
            && signedIn.CanClockOut;

        return JsonSerializer.Serialize(
            new
            {
                signedIn.Title,
                signedIn.CustomState,
                signedIn.SurfaceColorHex,
                signedIn.AccentColorHex,
                IsSignedOut = false,
                IsSignedIn = true,
                Message = string.Empty,
                PrimaryActionLabel = string.Empty,
                WidgetCardSurfaceBg = DefaultWidgetCardSurfaceBg,
                HasProfilePhoto = !string.IsNullOrEmpty(safeProfilePhotoUrl),
                ProfilePhotoUrl = safeProfilePhotoUrl,
                signedIn.DisplayName,
                signedIn.StatusHeadline,
                signedIn.StatusDetail,
                signedIn.SessionCounter,
                signedIn.LastAction,
                signedIn.LastActionTime,
                signedIn.LastCompletedShiftDuration,
                signedIn.WorkedThisMonthDuration,
                signedIn.CoffeeTodayDuration,
                signedIn.FoodTodayDuration,
                signedIn.TimelineText,
                TimerChipSvg = BuildTimerChipSvg(signedIn.SessionCounter, isActiveSession),
                CoffeeVerb = signedIn.ActiveBreakType == BreakType.Coffee
                    ? "end-coffee-break" : "start-coffee-break",
                FoodVerb = signedIn.ActiveBreakType == BreakType.Food
                    ? "end-food-break" : "start-food-break",
                ShowPrimaryActionInteractive = signedIn.CanClockIn
                    || (signedIn.ActiveBreakType == BreakType.None && signedIn.CanClockOut),
                ShowPrimaryActionDisabled = signedIn.ActiveBreakType != BreakType.None,
                PrimaryActionButton = signedIn.CanClockIn ? EntryBlue : StopBlue,
                PrimaryActionTitle = signedIn.CanClockIn ? "Empezar jornada" : "Finalizar jornada",
                PrimaryActionVerb = signedIn.CanClockIn ? "clock-in" : "clock-out",
                ShowCoffeeActive = signedIn.ActiveBreakType == BreakType.None && signedIn.CanStartCoffeeBreak,
                ShowCoffeeEndBreak = signedIn.ActiveBreakType == BreakType.Coffee && signedIn.CanEndCoffeeBreak,
                ShowCoffeeDisabled = signedIn.ActiveBreakType == BreakType.Food
                    || (signedIn.ActiveBreakType == BreakType.None && !signedIn.CanStartCoffeeBreak)
                    || (signedIn.ActiveBreakType == BreakType.Coffee && !signedIn.CanEndCoffeeBreak),
                ShowFoodActive = signedIn.ActiveBreakType == BreakType.None && signedIn.CanStartFoodBreak,
                ShowFoodEndBreak = signedIn.ActiveBreakType == BreakType.Food && signedIn.CanEndFoodBreak,
                ShowFoodDisabled = signedIn.ActiveBreakType == BreakType.Coffee
                    || (signedIn.ActiveBreakType == BreakType.None && !signedIn.CanStartFoodBreak)
                    || (signedIn.ActiveBreakType == BreakType.Food && !signedIn.CanEndFoodBreak)
            },
            SerializerOptions);
    }
}

