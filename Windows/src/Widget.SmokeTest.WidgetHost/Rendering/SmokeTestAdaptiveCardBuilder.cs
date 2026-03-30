using System.Text.Json;

namespace Widget.SmokeTest.WidgetHost.Rendering;

internal static class SmokeTestAdaptiveCardBuilder
{
    private static readonly JsonSerializerOptions SerializerOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    private const string Template = """
    {
      "$schema": "http://adaptivecards.io/schemas/adaptive-card.json",
      "type": "AdaptiveCard",
      "version": "1.5",
      "body": [
        {
          "type": "TextBlock",
          "text": "${title}",
          "weight": "Bolder",
          "size": "Medium",
          "wrap": true
        },
        {
          "type": "TextBlock",
          "text": "${status}",
          "wrap": true
        },
        {
          "type": "FactSet",
          "facts": [
            {
              "title": "Pings",
              "value": "${pingCount}"
            },
            {
              "title": "Última acción",
              "value": "${lastAction}"
            },
            {
              "title": "Hora",
              "value": "${lastActionTime}"
            }
          ]
        }
      ],
      "actions": [
        {
          "type": "Action.Execute",
          "title": "Ping",
          "verb": "smoke-ping"
        }
      ]
    }
    """;

    public static string BuildTemplate() => Template;

    public static string BuildData(SmokeTestWidgetViewModel viewModel) =>
        JsonSerializer.Serialize(viewModel, SerializerOptions);
}
