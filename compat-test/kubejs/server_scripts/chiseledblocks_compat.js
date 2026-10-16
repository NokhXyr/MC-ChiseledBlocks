// Chiseled Blocks compatibility test (runClientCompat only).
// Uses Chiseled Blocks items in KubeJS recipes, including a bit whose material is a data component.
ServerEvents.recipes(event => {
  event.shapeless('chiseledblocks:iron_chisel', ['minecraft:dirt', 'minecraft:dirt'])
    .id('chiseledblocks_compat:chisel_from_dirt')
  event.shapeless(Item.of('chiseledblocks:pixel', 16, '[chiseledblocks:pixel_material={Name:"minecraft:stone"}]'), ['minecraft:cobblestone'])
    .id('chiseledblocks_compat:stone_bits')
})
