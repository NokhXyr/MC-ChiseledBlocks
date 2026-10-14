// Bitsmith compatibility test (runClientCompat only).
// Uses Bitsmith items in KubeJS recipes, including a bit whose material is a data component.
ServerEvents.recipes(event => {
  event.shapeless('bitsmith:iron_chisel', ['minecraft:dirt', 'minecraft:dirt'])
    .id('bitsmith_compat:chisel_from_dirt')
  event.shapeless(Item.of('bitsmith:bit', 16, '[bitsmith:bit_material={Name:"minecraft:stone"}]'), ['minecraft:cobblestone'])
    .id('bitsmith_compat:stone_bits')
})
