import { StyleSheet, View } from 'spotifyplus/react'

const CircleView = () => {
    return (
        <View style={styles.container}>
            <View style={styles.circle} />
        </View>
    )
}

const styles = StyleSheet.create({
    container: {
        flex: 1,
        justifyContent: 'center',
        alignItems: 'center'
    },
    circle: {
        width: 100,
        height: 100,
        borderRadius: 50,
        backgroundColor: 'white',
        justifyContent: 'center',
        alignItems: 'center'
    }
});

export default CircleView